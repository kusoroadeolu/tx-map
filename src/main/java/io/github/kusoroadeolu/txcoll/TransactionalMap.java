package io.github.kusoroadeolu.txcoll;

import io.github.kusoroadeolu.txcoll.handlers.CommitHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

public class TransactionalMap<K, V> {
    private final Map<K, V> underlying;

    //Shared state
    private final KeyToLockers<K> keyToLockers;
    private final SynchronizedTxSet sizeLockers;

    TransactionalMap(Map<K, V> underlying, KeyToLockers<K> keyToLockers, SynchronizedTxSet sizeLockers) {
        this.underlying = underlying;
        this.keyToLockers = keyToLockers;
        this.sizeLockers = sizeLockers;
    }

    public TransactionalMap(){
        this(new ConcurrentHashMap<>(), new KeyToLockers<>(), new SynchronizedTxSet());
    }

    public boolean containsKey(K key){
        return this.underlying.containsKey(key);
    }

    public static class MapTransaction<K, V> implements Transaction{
        //This transactional map
        private final TransactionalMap<K, V> map;

        //Local fields
        private Map<K, CompletableOperation<?>> ops;
        private final List<ChildMapTransaction<K, V>> txs;
        private int delta = 0;


        //Open nested
        public MapTransaction(TransactionalMap<K, V> map){
            this.map = map;
            this.txs = new ArrayList<>();
        }


         public void put(K key, V value) {
            var op = new Operation.PutOperation<>(value);
            ops.put(key, new CompletableOperation<>(op, new CompletableFuture<>()));
            map.keyToLockers.put(key, op, new ChildMapTransaction<>(this, op));
         }

         public CompletableFuture<V> get(K key) {
            var op = Operation.GetOperation.GET;
            var future = new CompletableFuture<V>();
            ops.put(key, new CompletableOperation<>(op, future));
             map.keyToLockers.put(key, op, new ChildMapTransaction<>(this, op));
            return future;
         }

         public CompletableFuture<V> remove(K key) {
             var op = Operation.RemoveOperation.REMOVE;
             var future = new CompletableFuture<V>();
             ops.put(key, new CompletableOperation<>(op, future));
             map.keyToLockers.put(key, op, new ChildMapTransaction<>(this, op));
             return future;
         }

         public CompletableFuture<Boolean> containsKey(K key){
             var op = Operation.ContainsKeyOperation.CONTAINS;
             var future = new CompletableFuture<Boolean>();
             ops.put(key, new CompletableOperation<>(op, future));
             map.keyToLockers.put(key, op, new ChildMapTransaction<>(this, op));
             return future;
         }

         public CompletableFuture<Integer> size(K key){
             var op = Operation.SizeOperation.SIZE;
             var future = new CompletableFuture<Integer>();
             ops.put(key, new CompletableOperation<>(op, future));
             map.keyToLockers.put(key, op, new ChildMapTransaction<>(this, op));
             return future;
         }

         void incrementDelta(){
            delta++;
         }

        void decrementDelta(){
            delta--;
        }


         public void commit() {
            for (Map.Entry<K, CompletableOperation<?>> entry : ops.entrySet()){

            }
         }

         public void abort() {

         }
     }

    static class ChildMapTransaction<K, V> implements Transaction {
            private final MapTransaction<K, V> parent;
            private final Operation operation;
            private final List<Lock> locks;
            private final AtomicReference<TransactionState> state;
            private final CommitHandler handler;

        public ChildMapTransaction(MapTransaction<K, V> parent, Operation operation) {
            this.operation = operation;
            this.parent = parent;
            this.state = new AtomicReference<>();
            this.locks = new ArrayList<>(0);
            this.handler = new MapCommitHandler<>(this);
        }

        public void commit() {

        }


        public void abort() {

        }
    }

    record MapCommitHandler<K, V>(Transaction tx) implements CommitHandler {
        public void commit() {
            this.handleType();
        }

        void handleType(){
            switch (tx){
                case MapTransaction<K, V>  mtx-> handleParent();
                case ChildMapTransaction<K, V> cmtx -> ;
                default -> throw new IllegalArgumentException(""); //Should never happen
            }
        }

        void handleParent(){}

        void handleChild(ChildMapTransaction<K, V> cmtx){
            var op = cmtx.operation;
            var map = cmtx.parent.map;
            switch (op){
                case Operation.PutOperation<V>
            }
        }
    }




    record CompletableOperation<T>(Operation op, CompletableFuture<T> future){
         public void complete(T value){
             future.complete(value);
         }
    }
}
