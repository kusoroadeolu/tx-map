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
    private final Combiner<Map<K, V>> combiner;

    public FlatCombinedTxMap() {
        this(CombinerType.UNBOUND);
    }

    public FlatCombinedTxMap(CombinerType type){
        this.map = new HashMap<>();
        combiner = switch (type){
            case ARRAY -> new AtomicArrayCombiner<>(map);
            case UNBOUND -> new UnboundCombiner<>(map);
            case SEMAPHORE -> new SemaphoreCombiner<>(map);
        };
    }


    @Override
    public MapTransaction<K, V> beginTx() {
        return new CombinedMapTransaction<>(this);
    }

    public Map<K, V> map(){
        return map;
    }


    private static class CombinedMapTransaction<K, V> implements MapTransaction<K, V>{
        private final List<FutureActionWrapper<K, V>> actions;
        private final Map<K, V> underlying;
        private TransactionState state = TransactionState.SCHEDULED;
        private final Combiner<Map<K, V>> combiner;

        public CombinedMapTransaction(FlatCombinedTxMap<K, V> txMap) {
            this.actions = new ArrayList<>();
            this.underlying = txMap.map;
            this.combiner = txMap.combiner;
        }

        @Override
        public FutureValue<Option<V>> put(K key, V value) {
            Action<Map<K, V>, Object> putAction = _ -> underlying.put(key, value);
            FutureValue<Option<V>> fv = new FutureValue<>();
            actions.add(new FutureActionWrapper<>(putAction, fv));
            return fv;
        }

        @Override
        public FutureValue<Option<V>> remove(K key) {
            Action<Map<K, V>, Object> rmvAction = _ -> underlying.remove(key);
            FutureValue<Option<V>> fv = new FutureValue<>();
            actions.add(new FutureActionWrapper<>(rmvAction, fv));
            return fv;
        }

        @Override
        public FutureValue<Option<V>> get(K key) {
            Action<Map<K, V>, Object> getAction = _ -> underlying.get(key);
            FutureValue<Option<V>> fv = new FutureValue<>();
            actions.add(new FutureActionWrapper<>(getAction, fv));
            return fv;
        }

        @Override
        public FutureValue<Boolean> containsKey(K key) {
            Action<Map<K, V>, Object> containsKey = _ -> underlying.containsKey(key);
            FutureValue<Boolean> fv = new FutureValue<>();
            actions.add(new FutureActionWrapper<>(containsKey, fv));
            return fv;
        }

        @Override
        public FutureValue<Integer> size() {
            Action<Map<K, V>, Object> sizeAction = _ -> underlying.size();
            FutureValue<Integer> fv = new FutureValue<>();
            actions.add(new FutureActionWrapper<>(sizeAction, fv));
            return fv;
        }

        @Override
        public boolean isCommitted() {
            return state == TransactionState.COMMITTED;
        }

        @Override
        public void commit() {
            if (state == TransactionState.ABORTED) throw new RuntimeException("Cannot commit an aborted tx");
            combiner.combine(map -> {
                for (FutureActionWrapper<K, V> fw : actions) {
                    Object result = fw.action().apply(map);
                    fw.fv().complete(Option.ofNullable(result));
                }
                return null;
            });
            actions.clear();
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

    record FutureActionWrapper<K, V>(Action<Map<K, V>, Object> action, FutureValue<?> fv) {}
}
