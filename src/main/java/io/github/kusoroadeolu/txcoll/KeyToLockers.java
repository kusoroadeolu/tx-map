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
        var map = keyToLockers.computeIfAbsent(key, _ -> new ConcurrentHashMap<>());
        var txSet = map.computeIfAbsent(op, _ -> new SynchronizedTxSet());
        txSet.put(tx);
    }

    Option<SynchronizedTxSet> get(K key, Operation op){
        return Option.ofNullable(keyToLockers.get(key).get(op));
    }


}
