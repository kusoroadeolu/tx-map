package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.txmap.map.OptimisticTransactionalMap;

public interface TransactionalMap<K, V> {
    static <K, V>TransactionalMap<K,  V> create(){
        return new OptimisticTransactionalMap<>();
    }

    MapTransaction<K, V> beginTx();
}
