package io.github.kusoroadeolu.txcoll.map;

import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.txcoll.Transaction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeyToLockers<K> {
    private final Map<K, Map<Operation, GuardedTxSet>> keyToLockers;

    public KeyToLockers() {
        this.keyToLockers = new ConcurrentHashMap<>();
    }

    public void put(K key, Operation op, Transaction tx){
        var map = keyToLockers.computeIfAbsent(key, _ -> new ConcurrentHashMap<>());
        var txSet = map.computeIfAbsent(op, _ -> new GuardedTxSet());
        txSet.put(tx);
    }

    public Option<GuardedTxSet> getOrCreate(K key, Operation op){
        var map = keyToLockers.computeIfAbsent(key, _ -> new ConcurrentHashMap<>());
        var txSet = map.computeIfAbsent(op, _ -> new GuardedTxSet());
        return Option.some(txSet);
    }


}
