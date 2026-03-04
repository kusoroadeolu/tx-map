package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.ferrous.option.Option;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.github.kusoroadeolu.txmap.Combiner.getInstance;

public class SegmentedCombinedTxMap<K, V> implements TransactionalMap<K, V> {
    private final ConcurrentMap<K, V> map;
    private final Combiner.IdleStrategy idleStrategy; //Idle strat for each combiner
    private final CombinerType type;
    private final Combiner<ConcurrentMap<K, V>> sizeCombiner;
    private final ConcurrentMap<K, Combiner<ConcurrentMap<K, V>>> keyToCombiners;

    public SegmentedCombinedTxMap(Combiner.IdleStrategy idleStrategy, CombinerType combinerType) {
        this.map = new ConcurrentHashMap<>();
        this.keyToCombiners = new ConcurrentHashMap<>();
        this.idleStrategy = idleStrategy;
        this.type = combinerType;
        this.sizeCombiner = getInstance(type, map);
    }

    @Override
    public MapTransaction<K, V> beginTx() {
        return new SegmentedCombinedMapTransaction<>(this);
    }

    static class SegmentedCombinedMapTransaction<K, V> implements MapTransaction<K, V>{

        private final List<FutureActionWrapper<K, V>> sizeFutures;
        private final Map<K, List<FutureActionWrapper<K, V>>> keyToFuture;
        private final ConcurrentMap<K, V> map;
        private final CombinerType type;
        private final ConcurrentMap<K, Combiner<ConcurrentMap<K, V>>> keyToCombiners;
        private final Combiner<ConcurrentMap<K, V>> sizeCombiner;
        private TransactionState state = TransactionState.SCHEDULED;
        private final Combiner.IdleStrategy strategy;

        public SegmentedCombinedMapTransaction(SegmentedCombinedTxMap<K, V> txMap) {
            this.sizeFutures = new ArrayList<>();
            this.keyToFuture = new HashMap<>();
            this.map = txMap.map;
            this.keyToCombiners = txMap.keyToCombiners;
            this.type = txMap.type;
            this.sizeCombiner = txMap.sizeCombiner;
            this.strategy = txMap.idleStrategy;
        }

        @Override
        public FutureValue<Option<V>> put(K key, V value) {
            Action<Map<K, V>, Object> putAction = _ -> map.put(key, value);
            FutureValue<Option<V>> fv = new FutureValue<>();
            keyToFuture.computeIfAbsent(key, _ -> new ArrayList<>()).add(new FutureActionWrapper<>(putAction, fv));
            return fv;
        }

        @Override
        public FutureValue<Option<V>> remove(K key) {
            Action<Map<K, V>, Object> rmvAction = _ -> map.remove(key);
            FutureValue<Option<V>> fv = new FutureValue<>();
            keyToFuture.computeIfAbsent(key, _ -> new ArrayList<>()).add(new FutureActionWrapper<>(rmvAction, fv));
            return fv;
        }

        @Override
        public FutureValue<Option<V>> get(K key) {
            Action<Map<K, V>, Object> getAction = _ -> map.get(key);
            FutureValue<Option<V>> fv = new FutureValue<>();
            keyToFuture.computeIfAbsent(key, _ -> new ArrayList<>()).add(new FutureActionWrapper<>(getAction, fv));
            return fv;
        }

        @Override
        public FutureValue<Boolean> containsKey(K key) {
            Action<Map<K, V>, Object> containsKey = _ -> map.containsKey(key);
            FutureValue<Boolean> fv = new FutureValue<>();
            keyToFuture.computeIfAbsent(key, _ -> new ArrayList<>()).add(new FutureActionWrapper<>(containsKey, fv));
            return fv;
        }

        @Override
        public FutureValue<Integer> size() {
            Action<Map<K, V>, Object> sizeAction = _ -> map.size();
            FutureValue<Integer> fv = new FutureValue<>();
            sizeFutures.add(new FutureActionWrapper<>(sizeAction, fv));
            return fv;
        }


        Combiner<ConcurrentMap<K, V>> getCombiner(K key){
            if (key == null) return sizeCombiner;
            var combiner = keyToCombiners.get(key);
            if (combiner == null) combiner = keyToCombiners.computeIfAbsent(key, _ -> Combiner.getInstance(type, map));
            return combiner;
        }

        @Override
        public boolean isCommitted() {
            return state == TransactionState.COMMITTED;
        }


        //Writes per key per transaction are serialized, however, for reads, transactions could see dirty values from other transactions mid-flight(even though reads are serialized as well) so basically we'll see the last write from the last transaction that wrote to that specific key in the map, so is the nature of this transactional map
        @Override
        public void commit() {
            for (Map.Entry<K, List<FutureActionWrapper<K, V>>> entry: keyToFuture.entrySet()){
                var key = entry.getKey();
                var futures = entry.getValue();
                this.getCombiner(key).combine(_ -> {
                    for (FutureActionWrapper<K, V> fw : futures) {
                        Object result = fw.action().apply(this.map);
                        fw.fv().complete(Option.ofNullable(result));
                    }
                    return null;
                }, strategy);
            }

            if(!sizeFutures.isEmpty()){
                sizeCombiner.combine(_ -> {
                    for (FutureActionWrapper<K, V> fw : sizeFutures) {
                        Object result = fw.action().apply(this.map);
                        fw.fv().complete(Option.ofNullable(result));
                    }
                    return null;
                }, strategy);
            }

            keyToFuture.clear();
            sizeFutures.clear();
            state = TransactionState.COMMITTED;
        }

        @Override
        public void abort() {
            throw new UnsupportedOperationException();
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
