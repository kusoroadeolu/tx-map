package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.txmap.pessimistic.PessimisticTransactionalMap;

public interface TransactionalMap<K, V> {
    static <K, V>TransactionalMap<K,  V> createPessimistic(){
        return new PessimisticTransactionalMap<>();
    }

    static <K, V>TransactionalMap<K,  V> createSnapshot(){
        return new PessimisticTransactionalMap<>();
    }

    MapTransaction<K, V> beginTx();
}
