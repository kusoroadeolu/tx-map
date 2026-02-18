package io.github.kusoroadeolu.txcoll;

import io.github.kusoroadeolu.ferrous.option.None;
import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.ferrous.option.Some;
import io.github.kusoroadeolu.txcoll.handlers.AbortHandler;
import io.github.kusoroadeolu.txcoll.handlers.CommitHandler;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
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

    public static class MapTransaction<K, V> implements Transaction{
        //This transactional map
        private final TransactionalMap<K, V> txMap;

        //Local fields
        private final Map<K, Option<V>> storeBuf = new HashMap<>();
        private final List<ChildMapTransaction<K, V>> txs;
        private final Set<Lock> heldLocks;
        private int delta = 0;
        private final AtomicReference<TransactionState> state;


        //Open nested
        public MapTransaction(TransactionalMap<K, V> txMap){
            this.txMap = txMap;
            this.heldLocks = new HashSet<>();
            this.txs = new ArrayList<>();
            this.state = new AtomicReference<>();
        }

         public void put(K key, V value) {
            var op = new Operation.PutOperation<>(value);
            var future = new FutureValue<>();
            var ctx = new ChildMapTransaction<>(this, op, Option.some(key), future);
            this.txs.add(ctx);
         }


         //WRITE OPS
         @SuppressWarnings("unchecked")
         public FutureValue<V> get(K key) {
             var future = new FutureValue<V>();
             return (FutureValue<V>) this.registerReadOp(key, GET, future);
         }

         public FutureValue<Option<V>> remove(K key) {
             var op = Operation.RemoveOperation.REMOVE;
             var future = new FutureValue<Option<V>>();
             this.txs.add(new ChildMapTransaction<>(this, op, Option.some(key), future));
             return future;
         }

         // READ OPS
         FutureValue<?> registerReadOp(@Nullable K key, Operation op, FutureValue<?> future){
             var nullable = Option.ofNullable(key);
             var lock = this.holdReadLock(op, nullable);
             var ctx = new ChildMapTransaction<>(this, op, nullable, future, Option.some(lock));
             this.txs.add(ctx);
             txMap.keyToLockers.put(key, op, ctx);
             return future;
         }

        @SuppressWarnings("unchecked")
        public FutureValue<Boolean> containsKey(K key){
            var future = new FutureValue<Boolean>();
            return (FutureValue<Boolean>) this.registerReadOp(key, CONTAINS, future);
        }

         @SuppressWarnings("unchecked")
         public FutureValue<Integer> size(){
             var op = Operation.SizeOperation.SIZE;
             var future = new FutureValue<Integer>();
             return (FutureValue<Integer>) registerReadOp(null, op, future);
         }

         Lock holdReadLock(Operation op, Option<K> key){
           return switch (key){
                case Some<K> s -> txMap.keyToLockers.get(s.unwrap(), op)
                        .map(SynchronizedTxSet::rLock) //Get the read lock
                        .andThen(lock -> {
                            if (heldLocks.add(lock)) lock.lock();
                            return new Some<>(lock);
                        }).unwrap();
                case None<K> _ -> {
                   var lock = txMap.sizeLockers.rLock();
                   if (heldLocks.add(lock)) lock.lock();
                   yield lock;
                }
            };
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

        @Override
        public TransactionState state() {
            return state.get();
        }

        public void commit() {
            //Commit then countdown
            txs.forEach(ChildMapTransaction::commit);
            heldLocks.forEach(Lock::unlock); //Then unlock all locks
            txs.forEach(tx -> {
                tx.latch.countDown();
                switch (tx.operation){
                    case Operation.SizeOperation _ -> txMap.sizeLockers.remove(tx);
                    default -> {
                        var op = tx.operation;
                        var key = tx.key;
                        txMap.keyToLockers.get(key.unwrap(), op)
                                .ifSome(s -> s.remove(tx));
                    }
                }
            });
            state.set(TransactionState.COMMITTED);
        }

         public void abort() {
            throw new UnsupportedOperationException("Cannot abort parent transaction");
         }
     }

    static class ChildMapTransaction<K, V> implements Transaction {
            private final MapTransaction<K, V> parent;
            private final Option<K> key;
            private final Operation operation;
            final Option<Lock> lock;
            private boolean isModifying; //If this is a modifying transaction
            final AtomicReference<TransactionState> state;
            private final CommitHandler commitHandler;
            private final AbortHandler abortHandler;
            private final FutureValue<?> future;
            private final CountDownLatch latch; //This latch is used to prevent busy spinning while an writer is trying to abort an already validated transaction

        public ChildMapTransaction(MapTransaction<K, V> parent, Operation operation, Option<K> key, FutureValue<?> future, Option<Lock> lock) {
            this.operation = operation;
            this.lock = lock;
            this.parent = parent;
            this.state = new AtomicReference<>();
            this.commitHandler = new ChildTxCommitHandler<>(this);
            this.abortHandler = new ChildTxAbortHandler<>(this);
            this.key = key;
            this.future = future;
            this.latch = new CountDownLatch(1);
        }

        public ChildMapTransaction(MapTransaction<K, V> parent, Operation operation, Option<K> key, FutureValue<?> future){
            this(parent, operation, key, future, Option.none());
        }

        public boolean isAborted(){
            return state.get() == TransactionState.ABORTED;
        }

        public void setModifying(){
            this.isModifying = true;
        }

        public boolean isModifying(){
            return this.isModifying;
        }

        public Option<Transaction> parent() {
            return Option.some(parent);
        }

        public boolean addLock(Lock lock){
           return this.parent.heldLocks.add(lock);
        }

        public boolean containsLock(Lock lock){
            return this.parent.heldLocks.contains(lock);
        }

        public void commit() {
            commitHandler.commit();
        }

        //Only read ops can be aborted so we'll implement specifically for that
        public void abort() {
            this.abortHandler.abort();
        }

        public TransactionState state() {
            return state.get();
        }
    }


    interface MapTxCommitHandler extends CommitHandler{

    }

    record ChildTxCommitHandler<K, V>(ChildMapTransaction<K, V> cmtx) implements MapTxCommitHandler {

        @SuppressWarnings("unchecked")
        public void commit() {
            var op = cmtx.operation;
            var underlying = cmtx.parent.txMap.map;
            var key = cmtx.key.unwrap();
            var storeBuf = cmtx.parent.storeBuf;
            switch (op) {
                case Operation.PutOperation<?> po -> {
                    V v = (V) po.value();
                    storeBuf.put(key, Option.some(v));
                    underlying.put(key, v);
                    cmtx.state.compareAndSet(TransactionState.VALIDATED, TransactionState.COMMITTED);
                    cmtx.future.complete(v);
                    if (cmtx.isModifying()) cmtx.parent.incrementDelta();
                }

                case Operation.RemoveOperation _ -> {
                    var option =  storeBuf.remove(key);
                    storeBuf.put(key, Option.none()); //Put a none value
                    Option<V> prev = (Option<V>) switch (option){
                        case Some<?> some -> some;
                        case None<?> _ -> Option.ofNullable(underlying.remove(key));
                    };

                    cmtx.state.compareAndSet(TransactionState.VALIDATED, TransactionState.COMMITTED);
                    cmtx.future.complete(prev);
                    if (cmtx.isModifying()) cmtx.parent.decrementDelta();

                }

                case Operation.GetOperation _ -> {
                    var option = storeBuf.get(key);
                    Option<V> val = (Option<V>) switch (option){
                        case Some<?> some -> some;
                        case None<?> _ -> Option.ofNullable(underlying.get(key));
                    };
                    cmtx.state.compareAndSet(TransactionState.VALIDATED, TransactionState.COMMITTED);
                    cmtx.future.complete(val);
                }

                case Operation.ContainsKeyOperation _ -> {
                    var opt = storeBuf.get(key);
                    boolean val = (opt instanceof Some<?>) || underlying.containsKey(key);
                    cmtx.state.compareAndSet(TransactionState.VALIDATED, TransactionState.COMMITTED);
                    cmtx.future.complete(val);
                }

                case Operation.SizeOperation _ -> {
                    int size = underlying.size();
                    cmtx.state.compareAndSet(TransactionState.VALIDATED, TransactionState.COMMITTED);
                    cmtx.future.complete(size + cmtx.parent.delta);
                }

            }
        }

        public void validate(){
            var op = cmtx.operation;
            this.handleOperation(op);
        }

        void handleOperation(Operation op){
            var txMap = cmtx.parent.txMap;
            var key = cmtx.key.unwrap();
            switch (op){
                case Operation.PutOperation<?> _, Operation.RemoveOperation _ -> {
                    txMap.keyToLockers.get(key, op)
                            .map(SynchronizedTxSet::wLock)
                            .andThen(lock -> {
                                if (cmtx.addLock(lock)) lock.lock();
                                return null;
                            });

                    this.handleWriteOps(txMap, key, op);
                    cmtx.state.compareAndSet(TransactionState.NONE, TransactionState.VALIDATED);
                }

                //For read ops
                default -> {
                    if (!cmtx.state.compareAndSet(TransactionState.NONE, TransactionState.VALIDATED)){
                        if (cmtx.isAborted()) {
                            cmtx.state.compareAndSet(TransactionState.ABORTED, TransactionState.VALIDATED);
                            cmtx.parent.holdReadLock(cmtx.operation, cmtx.key);
                        }
                    }
                } //We want to CAS initially, if we cant, then it means we're aborted
            }
        }


        void handleWriteOps(TransactionalMap<K, V> txMap, K key, Operation op){
            //Ensure we only lock once, since a tx is basically only on a single thread, we cant really get deadlocks, but we want to ensure we release all locks
            //Then we want to grab to writeLocks for the contains operation, we want to check if the underlying map contains the key, so we can grab the size lock as well
            txMap.keyToLockers.get(key, CONTAINS)
                    .map(set -> {
                        var rLock = set.rLock();
                        if (cmtx.containsLock(rLock)) rLock.unlock(); //Unlock the read lock if we have it
                        return set;
                    })
                    .map(set -> set.abortAll(cmtx.parent.heldLocks)); //Then lock the write lock to prevent a situation where we cant use the write lock cuz we have the read lock
            //Once we've grabbed the lock for 'contains', we can check

            //Now that we have the lock for contains key , we can check the underlying map to see if we should obtain the size lock too
            boolean containsKey = txMap.map.containsKey(key);
             switch (op){
                case Operation.PutOperation<?> _ -> {
                    if (!containsKey){
                        var lock = txMap.sizeLockers.wLock();
                        if (cmtx.addLock(lock)) lock.lock();
                        cmtx.setModifying();
                    }
                }

                case Operation.RemoveOperation _ -> {
                    if (containsKey){
                        var lock = txMap.sizeLockers.wLock();
                        if (cmtx.addLock(lock)) lock.lock();
                        cmtx.setModifying();
                    }
                }

                default -> throw new Error();// Should never happen
            }

            //Do the same thing for GET ops as well
            txMap.keyToLockers.get(key, GET)
                    .map(set -> {
                        var rLock = set.rLock();
                        if (cmtx.containsLock(rLock)) rLock.unlock(); //Unlock the read lock if we have it
                        return set;
                    })
                    .map(set -> set.abortAll(cmtx.parent.heldLocks));
        }

    }


        interface MapTxAbortHandler<K, V> extends AbortHandler{

        }



        //TODO wire up aborts for parents
        record ChildTxAbortHandler<K, V>(ChildMapTransaction<K, V> cmtx) implements MapTxAbortHandler<K, V> {
            @Override
            public void abort() {
                if (!cmtx.state.compareAndSet(TransactionState.NONE, TransactionState.ABORTED) && !cmtx.isAborted()){
                    //Can only be committed or validated, so we wait till count down
                    try {
                        cmtx.latch.await();
                    } catch (InterruptedException e) {
                        //Just assume the transaction is aborted
                        //Maybe we can count down here, probably won't work lol
                    }
                }else {
                    var lock = cmtx.lock.unwrap();
                    if(cmtx.parent.heldLocks.remove(lock)) lock.unlock();
                }
            }


            public void abortByParent(){
                var txMap = cmtx.parent.txMap;
                cmtx.state.set(TransactionState.ABORTED);
                switch (cmtx.operation){
                    case Operation.SizeOperation _ -> txMap.sizeLockers.remove(cmtx);
                    default -> {
                        var op = cmtx.operation;
                        var key = cmtx.key;
                        txMap.keyToLockers.get(key.unwrap(), op)
                                .ifSome(s -> s.remove(cmtx));
                    }
                }

            }
        }

    }




