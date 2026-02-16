package io.github.kusoroadeolu.txcoll;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeyToLockers<K> {
    private final Map<K, Map<Operation, SynchronizedTxSet>> keyToLockers;

    public KeyToLockers() {
        this.keyToLockers = new ConcurrentHashMap<>();
    }

    public boolean put(K key, Operation op, Transaction tx){
         var opMap = keyToLockers.computeIfAbsent(key, _ -> new ConcurrentHashMap<>());
         var txSet = opMap.computeIfAbsent(op, _ -> new SynchronizedTxSet());
         return txSet.put(tx);
    }


}
