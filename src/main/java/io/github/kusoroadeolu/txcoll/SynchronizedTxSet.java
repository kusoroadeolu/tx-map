package io.github.kusoroadeolu.txcoll;

import java.util.HashSet;
import java.util.Set;

public class SynchronizedTxSet {
    private final Set<Transaction> txSet;

    public SynchronizedTxSet(){
        this.txSet = new HashSet<>();
    }

    public boolean put(Transaction tx){
        synchronized (txSet){
           return txSet.add(tx);
        }
    }
}
