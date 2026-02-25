package io.github.kusoroadeolu.txmap;


public interface TransactionalMap<K, V> {
    MapTransaction<K, V> beginTx();

    static <K, V>TransactionalMap<K, V> createFlatCombined(){
        return new FlatCombinedTxMap<>();
    }
}
