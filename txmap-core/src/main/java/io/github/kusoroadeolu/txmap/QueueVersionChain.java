package io.github.kusoroadeolu.txmap;

import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;

public class QueueVersionChain<E> implements VersionChain<E>{
    private final Deque<Version<E>> versionQueue;
    private int currentVersion = 0; //Should only be incremented by the lock holder
    private volatile Version<E> latest;
    private final EndTsHolder endTsHolder; //Constantly track the minimum of end ts of this version chain



    public QueueVersionChain() {
        this.versionQueue = new ConcurrentLinkedDeque<>();
        this.endTsHolder = new EndTsHolder();
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

        var ls = this.latest;

        //In the case where a writer modifies endTs before it is visible to us, we can fallback to the oLog(N) scenario
        if(ls != null && (tBegin >= ls.beginTs && tBegin < ls.endTs)) return ls;

        Version<E> overlap = null;

        Iterator<Version<E>> iterator = versionQueue.descendingIterator();
        while (iterator.hasNext()){
            Version<E> version = iterator.next();
            if (tBegin >= version.beginTs && tBegin < version.endTs){
                overlap = version;
                return overlap;
            }
        }

        return overlap;
    }


    public void removeUnreachableVersions(long tBegin){ //We're linking versions whose endTs < tBegin
        if (tBegin <= endTsHolder.endTs) return;
        endTsHolder.reset(); //Reset the holder everytime, to prevent a situation where we are sitting on an end ts, from a version pruned since
        var ls = this.latest;

        //Excluding the topmost version ofc
        versionQueue.removeIf(version -> {
            boolean shouldRemove = version.endTs < tBegin  && version != ls;

            if (!shouldRemove && version.endTs < endTsHolder.endTs) endTsHolder.endTs = version.endTs;
            return shouldRemove;
        });
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


    static class EndTsHolder{
        long endTs = Long.MAX_VALUE; //Only read by the gc thread so we don't need volatile here


        void reset(){
            endTs = Long.MAX_VALUE;
        }
    }
}

