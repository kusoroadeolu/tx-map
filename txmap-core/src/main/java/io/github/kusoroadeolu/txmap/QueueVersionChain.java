package io.github.kusoroadeolu.txmap;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class QueueVersionChain<E> implements VersionChain<E>{
    private final Deque<Version<E>> versionQueue;
    private int currentVersion = 0; //Should only be incremented by the lock holder
    private volatile Version<E> latest;


    public QueueVersionChain() {
        this.versionQueue = new ConcurrentLinkedDeque<>();
    }


    public Version<E> latest() {
        return latest;
    }

    public E enqueueNewVersion(E e, long beginTs, TransactionID txnId) {
        Version<E> prev = this.latest;
        Version<E> newVersion = new Version<>(e, ++currentVersion, beginTs, txnId);
        versionQueue.addLast(newVersion);
        if (prev != null) prev.setEndTs(beginTs);
        this.latest = newVersion;
        return prev == null ? null : prev.e;
    }


    public Version<E> findOverlap(long tBegin){
        if (versionQueue.isEmpty()) return null;
        Version<E> overlap = null;
        for (Version<E> version : versionQueue){
            if (tBegin >= version.beginTs && tBegin < version.endTs){
                overlap = version;
                break;
            }
        }

        return overlap;
    }


    public void removeUnreachableVersions(long tBegin){ //We're linking versions whose endTs < tBegin
        //Excluding the topmost version ofc
        versionQueue.removeIf(version -> version != latest && version.endTs < tBegin);
    }


    public int size(){
        return versionQueue.size();
    }

    @Override
    public String toString() {
        return "QueueVersionChain{" +
                "versionQueue=" + versionQueue +
                '}';
    }



}

