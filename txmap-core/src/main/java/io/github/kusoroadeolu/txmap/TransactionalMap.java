package io.github.kusoroadeolu.txmap;


import java.util.concurrent.CopyOnWriteArrayList;

public interface TransactionalMap<K, V> {

    static <K, V>TransactionalMap<K,  V> create(){
        return null;
    }

    MapTransaction<K, V> beginTx();
}
