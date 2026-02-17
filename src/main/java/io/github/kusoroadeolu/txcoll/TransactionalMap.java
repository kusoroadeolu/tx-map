package io.github.kusoroadeolu.txcoll;

import io.github.kusoroadeolu.ferrous.option.None;
import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.ferrous.option.Some;
import io.github.kusoroadeolu.txcoll.handlers.AbortHandler;
import io.github.kusoroadeolu.txcoll.handlers.CommitHandler;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import static io.github.kusoroadeolu.txcoll.Operation.ContainsKeyOperation.CONTAINS;
import static io.github.kusoroadeolu.txcoll.Operation.GetOperation.GET;

public class TransactionalMap<K, V> {
    private final Map<K, V> map;

    //Shared state
    private final KeyToLockers<K> keyToLockers;
    private final SynchronizedTxSet sizeLockers;

    TransactionalMap(Map<K, V> map, KeyToLockers<K> keyToLockers, SynchronizedTxSet sizeLockers) {
        this.map = map;
        this.keyToLockers = keyToLockers;
        this.sizeLockers = sizeLockers;
    }

    public TransactionalMap(){
        this(new ConcurrentHashMap<>(), new KeyToLockers<>(), new SynchronizedTxSet());
    }

    public boolean containsKey(K key){
        return this.map.containsKey(key);
    }

    public static class MapTransaction<K, V> implements Transaction{
        //This transactional map
        private final TransactionalMap<K, V> txMap;

        //Local fields
        private final Map<K, WriteValue> storeBuf = new HashMap<>();
        private final List<ChildMapTransaction<K, V>> txs;
        private final Set<Lock> heldLocks;
        private int delta = 0;


        //Open nested
        public MapTransaction(TransactionalMap<K, V> txMap){
            this.txMap = txMap;
            this.heldLocks = new HashSet<>();
            this.txs = new ArrayList<>();
        }

        //TODO Add optimistic reads for read based txs

         public void put(K key, V value) {
            var op = new Operation.PutOperation<>(value);
            var future = new FutureValue<>();
            var ctx = new ChildMapTransaction<>(this, op, Option.some(key), future);
            this.txs.add(ctx);
         }

         @SuppressWarnings("unchecked")
         public FutureValue<V> get(K key) {
             var future = new FutureValue<V>();
             return (FutureValue<V>) this.registerReadOp(key, GET, future);
         }

         public FutureValue<V> remove(K key) {
             var op = Operation.RemoveOperation.REMOVE;
             var future = new FutureValue<V>();
             this.txs.add(new ChildMapTransaction<>(this, op, Option.some(key), future));
             return future;
         }

         @SuppressWarnings("unchecked")
         public FutureValue<Boolean> containsKey(K key){
             var future = new FutureValue<Boolean>();
             return (FutureValue<Boolean>) this.registerReadOp(key, CONTAINS, future);
         }


         FutureValue<?> registerReadOp(@Nullable K key, Operation op, FutureValue<?> future){
             var ctx = new ChildMapTransaction<>(this, op, Option.ofNullable(key), future);
             this.txs.add(ctx);
             this.holdReadLock(op, Option.ofNullable(key));
             txMap.keyToLockers.put(key, op, ctx);
             return future;
         }

         @SuppressWarnings("unchecked")
         public FutureValue<Integer> size(){
             var op = Operation.SizeOperation.SIZE;
             var future = new FutureValue<Integer>();
             return (FutureValue<Integer>) registerReadOp(null, op, future);
         }

         void holdReadLock(Operation op, Option<K> key){
            switch (key){
                case Some<K> s -> txMap.keyToLockers.get(s.unwrap(), op)
                        .map(SynchronizedTxSet::rLock) //Get the read lock
                        .andThen(lock -> {
                            if (heldLocks.add(lock)) lock.lock();
                            return null;
                        });

                case None<K> _ -> {
                   var lock = txMap.sizeLockers.rLock();
                   if (heldLocks.add(lock)) lock.lock();
                }
            }

         }

         void incrementDelta(){
            delta++;
         }

        void decrementDelta(){
            delta--;
        }

        @Override
        public Option<Transaction> parent() {
            return Option.none();
        }

        public void commit() {
            for (;;);
         }

         public void abort() {

         }
     }

    static class ChildMapTransaction<K, V> implements Transaction {
            private final MapTransaction<K, V> parent;
            private final Option<K> key;
            private final Operation operation;
            private final AtomicReference<TransactionState> state;
            private final CommitHandler handler;
            private final FutureValue<?> future;

        public ChildMapTransaction(MapTransaction<K, V> parent, Operation operation, Option<K> key, FutureValue<?> future) {
            this.operation = operation;
            this.parent = parent;
            this.state = new AtomicReference<>();
            this.handler = new ChildTxCommitHandler<>(this);
            this.key = key;
            this.future = future;
        }

        @Override
        public Option<Transaction> parent() {
            return Option.some(parent);
        }

        public boolean addLock(Lock lock){
           return this.parent.heldLocks.add(lock);
        }

        public void commit() {

        }


        public void abort() {

        }
    }


    interface MapTxCommitHandler<K, V> extends CommitHandler{

    }

    record ChildTxCommitHandler<K, V>(ChildMapTransaction<K, V> cmtx) implements MapTxCommitHandler<K, V> {
        public void commit() {

        }

        public void validate(){
            var op = cmtx.operation;
            this.handleOperation(op);
        }

        void handleOperation(Operation op){
            var txMap = cmtx.parent.txMap;
            var sizeSet = txMap.sizeLockers;
            var key = cmtx.key.unwrap();
            switch (op){
                case Operation.PutOperation _, Operation.RemoveOperation _ -> {
                    txMap.keyToLockers.get(key, op)
                            .map(SynchronizedTxSet::wLock)
                            .andThen(lock -> {
                                if (cmtx.addLock(lock)) lock.lock();
                                return null;
                            });

                    this.handleWriteOps(txMap, key, op);
                }

                //For read ops
                default -> {

                } //We want to check if they are aborted, if so
            }
        }


        void handleWriteOps(TransactionalMap<K, V> txMap, K key, Operation op){
            //Ensure we only lock once, since a tx is basically only on a single thread, we cant really get deadlocks, but we want to ensure we release all locks
            //Then we want to grab to writeLocks for the contains operation, we want to check if the underlying map contains the key, so we can grab the size lock as well
            txMap.keyToLockers.get(key, CONTAINS)
                    .map(SynchronizedTxSet::abortAll)
                    .andThen(lock -> {
                        if (cmtx.addLock(lock)) lock.lock();
                        return null;
                    });
            //Once we've grabbed the lock for 'contains', we can check

            //Now that we have the lock for contains key , we can check the underlying map to see if we should obtain the size lock too
            boolean containsKey = txMap.map.containsKey(key);
             switch (op){
                case Operation.PutOperation _ -> {
                    if (!containsKey){
                        var lock = txMap.sizeLockers.wLock();
                        if (cmtx.addLock(lock)) lock.lock();
                    }
                }

                case Operation.RemoveOperation _ -> {
                    if (containsKey){
                        var lock = txMap.sizeLockers.wLock();
                        if (cmtx.addLock(lock)) lock.lock();
                    }
                }

                default -> throw new Error();// Should never happen
            }

            //Do the same thing for get as well
            txMap.keyToLockers.get(key, GET)
                    .map(SynchronizedTxSet::abortAll)
                    .andThen(lock -> {
                        if (cmtx.addLock(lock)) lock.lock();
                        return null;
                    });
        }

    }






        interface MapTxAbortHandler<K, V> extends AbortHandler{

        }


        record ChildTxAbortHandler<K, V>(ChildMapTransaction<K, V> cmtx) implements MapTxAbortHandler<K, V>{
            @Override
            public void abort() {

            }
        }

        record CompletableOperation<T>(Operation op, FutureValue<T> future){
            public void complete(T value){
                future.complete(value);
            }
        }

        interface WriteValue{
            record Value<V>(V value) implements WriteValue{
            }

            enum None implements WriteValue{
                REMOVED
            }
        }

    }




