package io.github.kusoroadeolu.txcoll;

import io.github.kusoroadeolu.ferrous.option.Option;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeyToLockers<K> {
    private final Map<K, Map<Operation, SynchronizedTxSet>> keyToLockers;

    public KeyToLockers() {
        this.keyToLockers = new ConcurrentHashMap<>();
    }

    public void put(K key, Operation op, Transaction tx){
         keyToLockers.computeIfAbsent(key, _ -> {
             var map = new ConcurrentHashMap<Operation, SynchronizedTxSet>();
             var txSet = map.computeIfAbsent(op, _ -> new SynchronizedTxSet());
             txSet.put(tx);
             return map;
         });
    }

    Option<SynchronizedTxSet> get(K key, Operation op){
        return Option.ofNullable(keyToLockers.get(key).get(op));
    }


}
