package io.github.kusoroadeolu.txmap.map;

import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.txmap.Transaction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeyToLockers<K> {
    private final Map<K, Map<Operation, GuardedTxSet>> keyToLockers;

    public KeyToLockers() {
        this.keyToLockers = new ConcurrentHashMap<>();
    }

    public void put(K key, Operation op, Transaction tx){
        var txSet = this.getOrCreate(key, op).unwrap();
        txSet.put(tx);
    }

    Option<GuardedTxSet> getOrCreate(K key, Operation op){
        var map = keyToLockers.get(key);
        GuardedTxSet set;
        if (map == null) map = keyToLockers.computeIfAbsent(key, _ -> new ConcurrentHashMap<>());

        set = map.get(op);
        if (set != null) return Option.some(set);
        else return Option.some(map.computeIfAbsent(op, _ -> new GuardedTxSet()));
    }


}
