package io.github.kusoroadeolu.txmap.vchain;

import io.github.kusoroadeolu.txmap.TransactionID;

/*
* 12, 4(Object header plus ), 4,4,
* */
public class Version<E>{
        private final static long INF = Long.MAX_VALUE;
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