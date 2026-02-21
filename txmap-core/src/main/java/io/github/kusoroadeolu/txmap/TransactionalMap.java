package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.txmap.copyonwrite.CopyOnWriteTransactionalMap;
import io.github.kusoroadeolu.txmap.pessimistic.PessimisticTransactionalMap;
import io.github.kusoroadeolu.txmap.snapshot.SnapshotTransactionalMap;

public interface TransactionalMap<K, V> {
    static <K, V>TransactionalMap<K,  V> createPessimistic(){
        return new PessimisticTransactionalMap<>();
    }

    static <K, V>TransactionalMap<K,  V> createSnapshot(){
        return new SnapshotTransactionalMap<>();
    }

    static <K, V>TransactionalMap<K,  V> createCopyOnWrite(){
        return new CopyOnWriteTransactionalMap<>();
    }

    MapTransaction<K, V> beginTx();
}
