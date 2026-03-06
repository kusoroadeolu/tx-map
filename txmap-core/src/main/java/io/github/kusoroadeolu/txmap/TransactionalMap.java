package io.github.kusoroadeolu.txmap;


import java.util.concurrent.CopyOnWriteArrayList;

public interface TransactionalMap<K, V> {

    static <K, V>TransactionalMap<K, V> create(){
        return new MvccTransactionalMap<>();
    }

    MapTransaction<K, V> beginTx();
}
