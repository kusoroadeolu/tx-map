package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.txmap.map.DefaultTransactionalMap;

public interface TransactionalMap<K, V> {
    static <K, V>TransactionalMap<K,  V> create(){
        return new DefaultTransactionalMap<>();
    }

    MapTransaction<K, V> beginTx();
}
