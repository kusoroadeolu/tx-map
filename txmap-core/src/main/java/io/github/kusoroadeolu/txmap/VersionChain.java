package io.github.kusoroadeolu.txmap;

import java.util.Deque;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

public class VersionChain<E> {
    private final Queue<Version<E>> versionQueue;
    private int currentVersion = 0; //Should only be incremented by the lock holder
    private volatile Version<E> latest;
    private final static long INF = Long.MAX_VALUE;

    public VersionChain() {
        this.versionQueue = new ConcurrentLinkedQueue<>();
    }


    public Version<E> latest() {
        return latest;
    }

    public E enqueueNewVersion(E e, long beginTs, TransactionID txnId){ //i.e. txcommit
        Version<E> prev = versionQueue.peek(); //This is always serialized so we can set check ifEmpty safely
        Version<E> latest = new Version<>(e, ++currentVersion, beginTs, txnId);
        versionQueue.add(latest); //Should only be incremented by the "holding tx"
        if (prev != null) prev.setEndTs(beginTs);
        this.latest = latest;
        return prev == null ? null : prev.e;
    }

    public Version<E> findOverlap(long tBegin){
        if (versionQueue.isEmpty()) return null;
        Version<E> overlap = null;
        for (Version<E> version : versionQueue){
            if (version.beginTs <= tBegin && tBegin < version.endTs){
                overlap = version;
                break;
            }
        }

        return overlap;
    }

    public void removeUnreachableVersions(long tBegin){ //We're linking versions whose endTs < tBegin
        //Excluding the topmost version ofc
        Iterator<Version<E>> it = versionQueue.iterator();
        int count = 0;
        while (it.hasNext()) {
            Version<E> version = it.next();
            if (count++ == 0) continue;
            if (version.endTs < tBegin) {
                it.remove();
            }
        }

    }

    public int size(){
        return versionQueue.size();
    }

    @Override
    public String toString() {
        return "VersionChain{" +
                "versionQueue=" + versionQueue +
                '}';
    }

    public static class Version<E>{
        final E e;
        final int versionNo;
        final long beginTs;
        final TransactionID txnId;
        volatile long endTs; //Dont need memory fences here just visibility, but for now lets just use volatile

        public Version(E e, int versionNo, long beginTs, TransactionID txnId) {
            this.e = e;
            this.versionNo = versionNo;
            this.txnId = txnId;
            this.beginTs = beginTs;
            this.endTs = INF;
        }

        public void setEndTs(long endTs) {
            this.endTs = endTs;
        }

        public E e() {
            return e;
        }

        public int versionNo() {
            return versionNo;
        }

        public long beginTs() {
            return beginTs;
        }

        public long endTs() {
            return endTs;
        }

        @Override
        public String toString() {
            return "Version{" +
                    "e=" + e +
                    ", versionNo=" + versionNo +
                    ", beginTs=" + beginTs +
                    ", txnId=" + txnId +
                    ", endTs=" + endTs +
                    '}';
        }
    }

}

