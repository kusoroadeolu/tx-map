package io.github.kusoroadeolu.txmap;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

//A simple map holding the tBegin numbers of each current transaction, when a key's version chain reaches a certain size,
// the write operation working on that key clears all unreachable versions i.e. V.endts <= tBegin
public class ActiveTransactions {
    private final ConcurrentMap<TransactionID, Long> map;
    public ActiveTransactions() {
        this.map = new ConcurrentHashMap<>();
    }

    public ActiveTransactions(ConcurrentMap<TransactionID, Long> map) {
        this.map = new ConcurrentHashMap<>(map);
    }

    void put(TransactionID txnId, long tBegin){
        this.map.put(txnId, tBegin);
    }

    void remove(TransactionID txnId){
        this.map.remove(txnId);

    }


    //Should only be called if a transaction has copied this map onto its thread stack
    long findMinActiveTBegin(){
        Set<Long> set = new HashSet<>(map.values()); //Copy to set to prevent duplicates and min traversals
        long min = 0;
        int count = 0;
        for (long l : set){
            if (count == 0 || l < min){
                min = l;
            }
            ++count;
        }

        return min;
    }

    ActiveTransactions copy(){
        return new ActiveTransactions(map);
    }

}
