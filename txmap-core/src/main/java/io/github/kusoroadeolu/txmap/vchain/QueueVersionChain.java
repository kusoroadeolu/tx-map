package io.github.kusoroadeolu.txmap.vchain;

import io.github.kusoroadeolu.txmap.TransactionID;

import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.LongAdder;

public class QueueVersionChain<E> implements VersionChain<E> {
    private final Deque<Version<E>> versionQueue;
    private int currentVersion = 0; //Should only be incremented by the lock holder
    private volatile Version<E> latest;
    private final MinVisibleEpoch minVisibleEpoch; //Constantly track the minimum of end ts of this version chain
    private final LongAdder size;



    public QueueVersionChain() {
        this.versionQueue = new ConcurrentLinkedDeque<>();
        this.minVisibleEpoch = new MinVisibleEpoch();
        this.size = new LongAdder();
    }


    public Version<E> latest() {
        return latest;
    }

    public E addNewVersion(E e, long beginTs, TransactionID txnId) {
        Version<E> prev = this.latest;
        Version<E> newVersion = new Version<>(e, ++currentVersion, beginTs, txnId);
        versionQueue.add(newVersion);
        size.increment();
        if (prev != null) prev.setEndTs(beginTs);
        this.latest = newVersion;
        return prev == null ? null : prev.e;
    }


    public Version<E> findOverlap(long tBegin){
        if (versionQueue.isEmpty()) return null;

        var ls = this.latest;

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


    public void pruneUnreachableVersions(long from){ //We're linking versions whose endTs < tBegin
        if (minVisibleEpoch.epoch != Long.MAX_VALUE && from <= minVisibleEpoch.epoch) return; //If the current end ts is greater than tBegin(the seen epoch), skip
        minVisibleEpoch.reset(); //Reset the holder everytime, to prevent a situation where we are sitting on an end ts, from a version pruned since]
        var ls = this.latest;
        versionQueue.removeIf(version -> {
            boolean shouldRemove = version.endTs < from && version != ls;
            if (!shouldRemove && version.endTs < minVisibleEpoch.epoch) minVisibleEpoch.epoch = version.endTs;
            else size.decrement();

            return shouldRemove;
        });

    }


    public int size(){
        return (int) size.sum();
    }

    @Override
    public String toString() {
        return "QueueVersionChain{" +
                "versionQueue=" + versionQueue +
                '}';
    }


    static class MinVisibleEpoch {
        long epoch = Long.MAX_VALUE; //Only read and modified by the gc thread so we don't need inter thread visibility here


        void reset(){
            epoch = Long.MAX_VALUE;
        }
    }
}
