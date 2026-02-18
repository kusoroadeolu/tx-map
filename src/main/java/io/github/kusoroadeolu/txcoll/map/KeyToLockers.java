package io.github.kusoroadeolu.txcoll.map;

import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.txcoll.Transaction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeyToLockers<K> {
    private final Map<K, Map<Operation, SynchronizedTxSet>> keyToLockers;

    public KeyToLockers() {
        this.keyToLockers = new ConcurrentHashMap<>();
    }

    public void put(K key, Operation op, Transaction tx){
        var map = keyToLockers.computeIfAbsent(key, _ -> new ConcurrentHashMap<>());
        var txSet = map.computeIfAbsent(op, _ -> new SynchronizedTxSet());
        txSet.put(tx);
    }

    Option<SynchronizedTxSet> get(K key, Operation op){
        var map = keyToLockers.computeIfAbsent(key, _ -> new ConcurrentHashMap<>());
        var txSet = map.computeIfAbsent(op, _ -> new SynchronizedTxSet());
        return Option.some(txSet);
    }


}
