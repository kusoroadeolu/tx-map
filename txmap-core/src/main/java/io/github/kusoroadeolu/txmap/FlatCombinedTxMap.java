package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.ferrous.option.Option;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FlatCombinedTxMap<K, V> implements TransactionalMap<K, V>{
    private final Map<K, V> map;

    public FlatCombinedTxMap() {
        this.map = new HashMap<>();
    }


    @Override
    public MapTransaction<K, V> beginTx() {
        return new CombinedMapTransaction<>(this);
    }

    public Map<K, V> map(){
        return map;
    }


    private static class CombinedMapTransaction<K, V> implements MapTransaction<K, V>{
        private final List<FutureActionWrapper<K, ?>> actions;
        private final Map<K, V> underlying;
        private TransactionState state = TransactionState.SCHEDULED;
        private final Combiner<BatchTxAction<K, V>> combiner;

        public CombinedMapTransaction(FlatCombinedTxMap<K, V> txMap) {
            this.actions = new ArrayList<>();
            this.underlying = txMap.map;
            this.combiner = new SemaphoreCombiner<>(new BatchTxAction<>(actions));
        }

        @Override
        public FutureValue<Option<V>> put(K key, V value) {
            Action<K, V> putAction = _ -> underlying.put(key, value);
            FutureValue<Option<V>> fv = new FutureValue<>();
            actions.add(new FutureActionWrapper<>(putAction, fv, key));
            return fv;
        }

        @Override
        public FutureValue<Option<V>> remove(K key) {
            Action<K, V> rmvAction = _ -> underlying.remove(key);
            FutureValue<Option<V>> fv = new FutureValue<>();
            actions.add(new FutureActionWrapper<>(rmvAction, fv, key));
            return fv;
        }

        @Override
        public FutureValue<Option<V>> get(K key) {
            Action<K, V> getAction = _ -> underlying.get(key);
            FutureValue<Option<V>> fv = new FutureValue<>();
            actions.add(new FutureActionWrapper<>(getAction, fv, key));
            return fv;
        }

        @Override
        public FutureValue<Boolean> containsKey(K key) {
            Action<K, Boolean> ctsAction = _ -> underlying.containsKey(key);
            FutureValue<Boolean> fv = new FutureValue<>();
            actions.add(new FutureActionWrapper<>(ctsAction, fv, key));
            return fv;
        }

        @Override
        public FutureValue<Integer> size() {
            Action<K, Integer> sizeAction = _ -> underlying.size();
            FutureValue<Integer> fv = new FutureValue<>();
            actions.add(new FutureActionWrapper<>(sizeAction, fv, null));
            return fv;
        }

        @Override
        public boolean isCommitted() {
            return state == TransactionState.COMMITTED;
        }

        @Override
        public void commit() {
            if (state == TransactionState.ABORTED) throw new RuntimeException("Cannot commit an aborted tx");
            combiner.combine(b -> b.apply(null));
            state = TransactionState.COMMITTED;
        }

        @Override
        public void abort() {
            if (state == TransactionState.COMMITTED) throw new RuntimeException("Cannot commit a committed tx");
            state = TransactionState.ABORTED;
            actions.clear();
        }

        @Override
        public Option<Transaction> parent() {
            return Option.none();
        }

        @Override
        public TransactionState state() {
            return state;
        }
    }

    record BatchTxAction<E, R>(List<FutureActionWrapper<E, ?>> batch) implements Action<E, R>{
        @Override
        public R apply(E e) {
            for (FutureActionWrapper<E, ?> wp : batch){
                Object value = wp.action().apply(wp.e);
                wp.fv.complete(value);
            }

            return null;
        }
    }

    record FutureActionWrapper<E, R>(Action<E, R> action, FutureValue<?> fv, E e){

    }
}
