package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.ferrous.option.Option;

public interface MapTransaction<K, V> extends AutoCloseable, Transaction {

    default void close(){
        if (!isCommitted()) abort();
    }

    FutureValue<Option<V>> put(K key, V value);

    FutureValue<Option<V>> remove(K key);

    FutureValue<V> get(K key);

    FutureValue<Boolean> containsKey(K key);

    FutureValue<Integer> size();

    boolean isCommitted();
}
