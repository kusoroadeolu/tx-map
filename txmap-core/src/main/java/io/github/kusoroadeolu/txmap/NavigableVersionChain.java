package io.github.kusoroadeolu.txmap;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;

public class NavigableVersionChain<E> implements VersionChain<E> {
    private final NavigableMap<Long, Version<E>> versionMap;
    private int currentVersion = 0; //Should only be incremented by the lock holder
    private volatile Version<E> latest;

    public NavigableVersionChain() {
        this.versionMap = new ConcurrentSkipListMap<>();
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
    public void removeUnreachableVersions(long tBegin) {
        Set<Map.Entry<Long, Version<E>>> set = versionMap.entrySet();
        set.removeIf(entry -> entry.getValue().endTs < tBegin  && entry.getValue() != latest);
    }
}
