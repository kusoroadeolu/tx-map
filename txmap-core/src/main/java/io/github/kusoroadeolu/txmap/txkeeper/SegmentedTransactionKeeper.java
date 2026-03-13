package io.github.kusoroadeolu.txmap.txkeeper;

import io.github.kusoroadeolu.txmap.TransactionID;

public class SegmentedTransactionKeeper {
    private static final int CPU = Runtime.getRuntime().availableProcessors();
    private final SerialTransactionKeeper[] keepers;

    public SegmentedTransactionKeeper() {
        this.keepers = new SerialTransactionKeeper[CPU];
        for (int i = 0; i < CPU; ++i){
            keepers[i] = new SerialTransactionKeeper();
        }
    }

    void put(TransactionID txnId, long tBegin){
        var activeTxKeeper = keepers[Math.toIntExact(txnId.txnId() % CPU)];
        activeTxKeeper.put(txnId, tBegin);
    }

    void remove(TransactionID txnId){
        var activeTxKeeper = keepers[Math.toIntExact(txnId.txnId() % CPU)];
        activeTxKeeper.remove(txnId);

    }

    //This is best effort and probably wont always be 100% accurate
    long minActiveTBegin(){
        long minTBegin = keepers[0].minActiveTBegin();
        for (int i = 1; i < CPU; ++i){
            minTBegin = Math.min(minTBegin, keepers[i].minActiveTBegin());
        }

        return minTBegin;
    }

    void stop(){
        for (int i = 0; i < CPU; ++i){
            keepers[i].stop();
        }
    }

}
