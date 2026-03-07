package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.txmap.MvccTransactionalMap.MvccTx;

import java.util.concurrent.atomic.AtomicReference;

public class KeyStatus {
    private final AtomicReference<Status> status;
    private final static Status NOT_HELD = new Status(-1);

    public KeyStatus() {
        this.status = new AtomicReference<>(NOT_HELD);
    }

    public boolean setHeld(TransactionID id){
        //No race condition here since it can either be zero or txn id
        return status.get().txnId() == id.txnId() || this.status.compareAndSet(NOT_HELD, new Status(id.txnId()));
    }

    public void setCommitted(){
        this.status.get().setCommitted();
    }

    public boolean isCommitted(){
        return this.status.get().isCommitted;
    }

    public boolean setNotHeld(TransactionID id){
        Status s = status.get();
        if (s.txnId == id.txnId()){
            return this.status.compareAndSet(s, NOT_HELD); //Ideally a set release should work well here, just to prevent me from abusing the api and concurrency bugs
        }

        return false;
    }

     <K, V> boolean isOwnedBy(MvccTx<K,V> kvMvccTx) {
        return this.status.get().txnId == kvMvccTx.txnId().txnId();
     }

    <K, V> boolean isHeld() {
        return this.status.get() != NOT_HELD;
    }

    static class Status{
        private final long txnId;
        private volatile boolean isCommitted = false;

        public Status(long txnId) {
            this.txnId = txnId;
        }

        long txnId(){
            return txnId;
        }

        void setCommitted(){
            this.isCommitted = true;
        }
    }
}
