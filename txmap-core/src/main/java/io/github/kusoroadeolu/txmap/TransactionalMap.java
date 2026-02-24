package io.github.kusoroadeolu.txmap;


public interface TransactionalMap<K, V> {
    MapTransaction<K, V> beginTx();
}
