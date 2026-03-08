package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.txmap.MvccTransactionalMap.MvccTx;

import java.util.concurrent.atomic.AtomicReference;

public class KeyStatus {
    private final AtomicReference<Status> status;
    private final static Status NOT_HELD = new Status(-1); //Just use a status, in case the long wraps around, so

    public KeyStatus() {
        this.status = new AtomicReference<>(NOT_HELD);
    }

    public boolean setHeld(TransactionID id){
        //No race condition here since it can either be zero or txn id
        return status.get().txnId() == id.txnId() || this.status.compareAndSet(NOT_HELD, new Status(id.txnId()));
    }


    public void setNotHeld(TransactionID id){
        Status s = status.get();
        if (s.txnId == id.txnId()){
            this.status.setRelease(NOT_HELD);
        }

    }

     <K, V> boolean isOwnedBy(MvccTx<K,V> kvMvccTx) {
        return this.status.get().txnId == kvMvccTx.txnId().txnId();
     }

    boolean isHeld() {
        return this.status.get() != NOT_HELD;
    }

    record Status(long txnId) {

    }
}
