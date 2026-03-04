package io.github.kusoroadeolu.txmap;


public interface TransactionalMap<K, V> {
    MapTransaction<K, V> beginTx();

    static <K, V>TransactionalMap<K, V> createFlatCombined(){
        return new FlatCombinedTxMap<>();
    }

    static <K, V>TransactionalMap<K, V> createFlatCombined(CombinerType type, Combiner.IdleStrategy strategy){
        return new FlatCombinedTxMap<>(type, strategy);
    }

    static <K, V>TransactionalMap<K, V> createSegmentedCombined(CombinerType type, Combiner.IdleStrategy strategy){
        return new SegmentedCombinedTxMap<>(strategy, type);
    }

    static <K, V>TransactionalMap<K, V> createFlatCombined(CombinerType type){
        return new FlatCombinedTxMap<>(type);
    }
}
