package io.github.kusoroadeolu.txcoll;

import io.github.kusoroadeolu.txcoll.map.DefaultTransactionalMap;

public interface TransactionalMap<K, V> {
    static <K, V>TransactionalMap<K,  V> create(){
        return new DefaultTransactionalMap<>();
    }

    MapTransaction<K, V> beginTx();
}
