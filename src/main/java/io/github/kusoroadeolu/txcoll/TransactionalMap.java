package io.github.kusoroadeolu.txcoll;

import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.ferrous.option.Some;
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
        private List<CompletableOperation<?>> ops;
        private final List<ChildMapTransaction<K, V>> txs;
        private int delta = 0;


        //Open nested
        public MapTransaction(TransactionalMap<K, V> map){
            this.map = map;
            this.txs = new ArrayList<>();
        }


         public void put(K key, V value) {
            var op = new Operation.PutOperation<>(value);
            ops.add(new CompletableOperation<>(op, new CompletableFuture<>()));
            map.keyToLockers.put(key, op, new ChildMapTransaction<>(this, op, Option.some(key)));
         }

         public CompletableFuture<V> get(K key) {
            var op = Operation.GetOperation.GET;
            return modify(key, op);
         }

         public CompletableFuture<V> remove(K key) {
             var op = Operation.RemoveOperation.REMOVE;
             return modify(key, op);
         }

         CompletableFuture<V> modify(K key, Operation op){
             var future = new CompletableFuture<V>();
             ops.add(new CompletableOperation<>(op, new CompletableFuture<>()));
             map.keyToLockers.put(key, op, new ChildMapTransaction<>(this, op, Option.some(key)));
             return future;
         }

         public CompletableFuture<Boolean> containsKey(K key){
             var op = Operation.ContainsKeyOperation.CONTAINS;
             var future = new CompletableFuture<Boolean>();
             ops.add(new CompletableOperation<>(op, new CompletableFuture<>()));
             map.keyToLockers.put(key, op, new ChildMapTransaction<>(this, op, Option.some(key)));
             return future;
         }

         public CompletableFuture<Integer> size(){
             var op = Operation.SizeOperation.SIZE;
             var future = new CompletableFuture<Integer>();
             ops.add(new CompletableOperation<>(op, new CompletableFuture<>()));
             map.sizeLockers.put(new ChildMapTransaction<>(this, op, Option.none()));
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
            private final Option<K> key;
            private final Operation operation;
            private final List<Lock> locks;
            private final AtomicReference<TransactionState> state;
            private final CommitHandler handler;

        public ChildMapTransaction(MapTransaction<K, V> parent, Operation operation, Option<K> key) {
            this.operation = operation;
            this.parent = parent;
            this.state = new AtomicReference<>();
            this.locks = new ArrayList<>(0);
            this.handler = new MapCommitHandler<>(this, locks);
            this.key = key;
        }

        public void commit() {

        }


        public void abort() {

        }
    }

    record MapCommitHandler<K, V>(Transaction tx, List<Lock> locks) implements CommitHandler {
        public void commit() {
            this.handleType();
        }

        public void validate(){
            switch (tx){
                case MapTransaction<K, V>  mtx-> validateParent();
                case ChildMapTransaction<K, V> cmtx -> validateChild(cmtx);
                default -> throw new IllegalArgumentException(""); //Should never happen
            }
        }


        //Here we want to handle operations differently
        //For writes, we first validate the transaction in its own set, we retry until we can
        //We then try to abort all transactions in conflicting semantic sets. We retry until we can
        //For reads, we just validate the transaction in its own set
        void validateChild(ChildMapTransaction<K, V> cmtx){
            var key2Lockers = cmtx.parent.map.keyToLockers;
            switch (cmtx.operation){
                case Operation.RemoveOperation ro -> {
                    var some = (Some<SynchronizedTxSet>) key2Lockers.get(cmtx.key.unwrap(), cmtx.operation);
                    var set = some.unwrap();
                    while(!set.validateSelf(cmtx));

                }

                case Operation.PutOperation po -> {
                    var some = (Some<SynchronizedTxSet>) key2Lockers.get(cmtx.key.unwrap(), cmtx.operation);
                    var set = some.unwrap();
                    set.validateSelf(cmtx);
                    }
                }
            }
        }

        void handleType(){
            switch (tx){
                case MapTransaction<K, V>  mtx-> commitParent();
                case ChildMapTransaction<K, V> cmtx -> ;
                default -> throw new IllegalArgumentException(""); //Should never happen
            }
        }

        void commitParent(){}

        void commitChild(ChildMapTransaction<K, V> cmtx){
            var op = cmtx.operation;
            var map = cmtx.parent.map;
            switch (op){
                case Operation.PutOperation<V> po -> {

                }
            }
        }
    }

    record CompletableOperation<T>(Operation op, CompletableFuture<T> future){
         public void complete(T value){
             future.complete(value);
         }
    }
}
