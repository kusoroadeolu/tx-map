package io.github.kusoroadeolu.txmap;


import io.github.kusoroadeolu.txmap.txkeeper.VersionChainType;

public interface TransactionalMap<K, V> {

    static <K, V>TransactionalMap<K, V> create(){
        return new MvccTransactionalMap<>();
    }

    static <K, V>TransactionalMap<K, V> create(VersionChainType type){
        return new MvccTransactionalMap<>(type);
    }

    static <K, V>TransactionalMap<K, V> create(int threshold){
        return new MvccTransactionalMap<>(threshold);
    }

    static <K, V>TransactionalMap<K, V> create(int threshold, VersionChainType versionChainType){
        return new MvccTransactionalMap<>(threshold, versionChainType);
    }

    MapTransaction<K, V> beginTx();

    void stop();
}
