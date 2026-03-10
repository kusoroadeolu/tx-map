package io.github.kusoroadeolu.txmap.vchain;

import io.github.kusoroadeolu.txmap.TransactionID;
import io.github.kusoroadeolu.txmap.VersionChain;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

public class NavigableVersionChain<E> implements VersionChain<E> {
    private final ConcurrentSkipListMap<Long, Version<E>> versionMap;
    private int currentVersion = 0; //Should only be incremented by the lock holder
    private volatile Version<E> latest;
    private final EndTsHolder endTsHolder; //Constantly track the minimum of end ts of this version chain


    public NavigableVersionChain() {
        this.versionMap = new ConcurrentSkipListMap<>();
        this.endTsHolder = new EndTsHolder();
    }

    @Override
    public E enqueueNewVersion(E e, long beginTs, TransactionID txnId) {
        Version<E> prev = this.latest;
        Version<E> newVersion = new Version<>(e, ++currentVersion, beginTs, txnId);
        versionMap.put(beginTs, newVersion); //Keyed by beginTs
        if (prev != null) prev.setEndTs(beginTs);
        this.latest = newVersion;
        return prev == null ? null : prev.e;
    }


    //tBegin >= version.beginTs && tBegin < version.endTs
    @Override
    public Version<E> findOverlap(long tBegin) {
        var ls = this.latest;

        //This is racy, in the case where a writer modifies endTs before it is visible to us, we can fallback to the oLog(N) scenario

        if (ls != null){
            long endTs = ls.endTs;
            if((tBegin >= ls.beginTs && tBegin < endTs)) return ls;
        }

        Map.Entry<Long, Version<E>> entry = versionMap.floorEntry(tBegin); // version.beginTs <= tBegin
        if (entry == null) return null;

        Version<E> version = entry.getValue();
        return tBegin < version.endTs ? version : null;

    }

    public Version<E> latest() {
        return latest;
    }

    @Override
    public int size() {
        return versionMap.size();
    }

    @Override
    //Ideally what we're looking for is how to reduce search time from O(N) to O(logN), since we're indexing by begints, and we kinda need to search by end ts, this would be hard
    public void removeUnreachableVersions(long tBegin) {
        if (tBegin <= endTsHolder.endTs) return;
        endTsHolder.reset(); //Reset the holder everytime, to prevent a situation where we are sitting on an end ts, from a version pruned since
        var ls = this.latest;
        Set<Map.Entry<Long, Version<E>>> set = versionMap.entrySet();



        //endTs < tBegin, but to find overlapping versions, we do tBegin < endTs && tBegin >= beginTs
        //So rather for beginTs, to find overlapping versions we can do beginTs >= tBegin, tBegin <= beginTs, but if we can do this, we'd get a map of the valid maps, which doesnt really help much lol
        set.removeIf(entry -> {
            var val = entry.getValue();
            boolean shouldRemove = val.endTs < tBegin  && val != ls;

            if (!shouldRemove && val.endTs < endTsHolder.endTs) endTsHolder.endTs = val.endTs;
            return shouldRemove;
        }); //Latest might get skipped due to GC thread race conditions, but its fine (i.e. what the GC saw as the latest is not the latest)
    }

    static class EndTsHolder{
        long endTs = Long.MAX_VALUE; //Only read by the gc thread so we don't need volatile here


        void reset(){
            endTs = Long.MAX_VALUE;
        }
    }
}
