package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.txmap.pessimistic.PessimisticTransactionalMap;

public interface TransactionalMap<K, V> {
    static <K, V>TransactionalMap<K,  V> create(){
        return new PessimisticTransactionalMap<>();
    }

    MapTransaction<K, V> beginTx();
}
