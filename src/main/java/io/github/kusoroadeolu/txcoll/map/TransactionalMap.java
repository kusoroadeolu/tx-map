package io.github.kusoroadeolu.txcoll.map;

import io.github.kusoroadeolu.ferrous.option.None;
import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.ferrous.option.Some;
import io.github.kusoroadeolu.txcoll.FutureValue;
import io.github.kusoroadeolu.txcoll.Transaction;
import io.github.kusoroadeolu.txcoll.TransactionState;
import io.github.kusoroadeolu.txcoll.handlers.AbortHandler;
import io.github.kusoroadeolu.txcoll.handlers.CommitHandler;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import static io.github.kusoroadeolu.txcoll.map.Operation.ContainsKeyOperation.CONTAINS;
import static io.github.kusoroadeolu.txcoll.map.Operation.GetOperation.GET;

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

    public MapTransaction<K, V> beginTx(){
        return new MapTransactionImpl<>(this);
    }

    static class MapTransactionImpl<K, V> implements MapTransaction<K, V> {
        //This transactional map
        final TransactionalMap<K, V> txMap;

        //Local fields
        final Map<K, Option<V>> storeBuf;
        final List<ChildMapTransaction<K, V>> txs;
        final Set<LockWrapper> heldLocks;
        int delta = 0;
        TransactionState state;
        private final AbortHandler abortHandler;
        private final CommitHandler commitHandler;


        public MapTransactionImpl(TransactionalMap<K, V> txMap){
            this.txMap = txMap;
            this.heldLocks = ConcurrentHashMap.newKeySet(); //In the case where two threads try to remove a lck from this set
            this.storeBuf = new HashMap<>();
            this.txs = new ArrayList<>();
            this.state = TransactionState.NONE;
            this.abortHandler = new MapTxAbortHandler<>(this);
            this.commitHandler = new MapTxCommitHandler<>(this);
        }

        //WRITE OPS

        public void put(K key, V value) {
            var op = new Operation.PutOperation<>(value);
            var future = new FutureValue<>();
            var ctx = new ChildMapTransaction<>(this, op, Option.some(key), future);
            this.txs.add(ctx);
        }

        @Override
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
        public FutureValue<V> get(K key) {
            var future = new FutureValue<V>();
            return (FutureValue<V>) this.registerReadOp(key, GET, future);
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
                        .map(SynchronizedTxSet::rLock) //Get the read lck
                        .andThen(lock -> {
                            var wp = new LockWrapper(LockType.READ, op , lock);
                            if (heldLocks.add(wp)) lock.lock();
                            return new Some<>(lock);
                        }).unwrap();
                case None<K> _ -> {
                   var lock = txMap.sizeLockers.rLock();
                    var wp = new LockWrapper(LockType.READ, op , lock);
                   if (heldLocks.add(wp)) lock.lock();
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
            return state;
        }

        public void commit() {
            this.commitHandler.validate();
            this.commitHandler.commit();
        }

         public void abort() {
            abortHandler.abort();
         }

         void clearAll(){
             storeBuf.clear();
             heldLocks.clear();
             txs.clear();
         }
     }


    record MapTxAbortHandler<K, V>(MapTransactionImpl<K, V> tx) implements AbortHandler{
        @Override
        public void abortByConflictingSemantic() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void abort() {
            tx.txs.forEach(ChildMapTransaction::abortByParent);
            tx.heldLocks.forEach(LockWrapper::unlock);
            tx.state = TransactionState.ABORTED;
            tx.clearAll();
        }
    }

    record MapTxCommitHandler<K, V>(MapTransactionImpl<K, V> tx) implements CommitHandler{
        @Override
        public void commit() {
            tx.txs.forEach(ChildMapTransaction::commit);
            tx.heldLocks.forEach(LockWrapper::unlock); //Then unlock all locks
            tx.txs.forEach(cmtx -> {
                cmtx.latch.countDown();
                switch (cmtx.operation){
                    case Operation.SizeOperation _ -> tx.txMap.sizeLockers.remove(cmtx);
                    default -> {
                        var op = cmtx.operation;
                        var key = cmtx.key;
                        tx.txMap.keyToLockers.get(key.unwrap(), op)
                                .ifSome(s -> s.remove(cmtx));
                    }
                }
            });
            tx.clearAll();
            tx.state = TransactionState.COMMITTED;
        }

        @Override
        public void validate() {
            tx.txs.forEach(ChildMapTransaction::validate);
            tx.state = TransactionState.VALIDATED;
        }
    }

    static class ChildMapTransaction<K, V> implements Transaction {
            private final MapTransactionImpl<K, V> parent;
            private final Option<K> key;
            final Operation operation;
            final Option<Lock> lock;
            private boolean isModifying; //If this is a modifying transaction
            final AtomicReference<TransactionState> state;
            private final CommitHandler commitHandler;
            private final AbortHandler abortHandler;
            private final FutureValue<?> future;
            private final CountDownLatch latch; //This latch is used to prevent busy spinning while an writer is trying to abort an already validated transaction

        public ChildMapTransaction(MapTransactionImpl<K, V> parent, Operation operation, Option<K> key, FutureValue<?> future, Option<Lock> lock) {
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

        public ChildMapTransaction(MapTransactionImpl<K, V> parent, Operation operation, Option<K> key, FutureValue<?> future){
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

        public boolean addLock(LockWrapper lock){
           return this.parent.heldLocks.add(lock);
        }

        public boolean containsLock(LockWrapper lock){
            return this.parent.heldLocks.contains(lock);
        }

        public void validate(){
            commitHandler.validate();
        }

        public void commit() {
            commitHandler.commit();
        }

        //Only read ops can be aborted so we'll implement specifically for that
        public void abort() {
            this.abortHandler.abortByConflictingSemantic();
        }

        public void abortByParent(){
            this.abortHandler.abort();
        }

        public TransactionState state() {
            return state.get();
        }
    }


    record ChildTxCommitHandler<K, V>(ChildMapTransaction<K, V> cmtx) implements CommitHandler {

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
            this.validateOps(op);
        }

        void validateOps(Operation op){
            var txMap = cmtx.parent.txMap;
            var key = cmtx.key.unwrap();
            switch (op){
                case Operation.PutOperation<?> _, Operation.RemoveOperation _ -> {
                    txMap.keyToLockers.get(key, op)
                            .map(SynchronizedTxSet::wLock)
                            .flatMap(lock -> {
                                var wp = new LockWrapper(LockType.WRITE, op, lock);
                                if (cmtx.addLock(wp)) lock.lock();
                                return new Some<>(lock);
                            });
                    this.handleWriteOps(txMap, key, op);
                    cmtx.state.compareAndSet(TransactionState.NONE, TransactionState.VALIDATED);
                }

                //For read ops
                default -> {
                    if (!cmtx.state.compareAndSet(TransactionState.NONE, TransactionState.VALIDATED)){
                        if (cmtx.isAborted()) {
                            cmtx.state.compareAndSet(TransactionState.ABORTED, TransactionState.VALIDATED);
                            cmtx.parent.holdReadLock(cmtx.operation, cmtx.key); //Set to validated first to prevent livelock issues
                        }
                    }
                } //We want to CAS initially, if we cant, then it means we're aborted
            }
        }


        void handleWriteOps(TransactionalMap<K, V> txMap, K key, Operation op){
            //Ensure we only lock once, since a tx is basically only on a single thread, we cant really get deadlocks, but we want to ensure we release all locks
            //Then we want to grab to writeLocks for the contains operation, we want to check if the underlying map contains the key, so we can grab the size lock as well
            //Then lock the write lock to prevent a situation where we cant use the write lock cuz we have the read lck
            var set = txMap.keyToLockers.get(key, op);
            this.releaseReadLockIfHeld(set, key, CONTAINS);

            //Now that we have the lck for contains key , we can check the underlying map to see if we should obtain the size lck too
            boolean containsKey = txMap.map.containsKey(key);
            var sizeSet = txMap.sizeLockers;
             switch (op){
                case Operation.PutOperation<?> _ -> {
                    if (!containsKey){
                        var wLock = sizeSet.wLock();
                        var wlw = new LockWrapper(LockType.WRITE, op, wLock);
                        if (cmtx.addLock(wlw)) {
                            this.releaseReadLockIfHeld(Option.some(sizeSet), key, op);
                            wLock.lock();
                        }
                        cmtx.setModifying();
                    }
                }

                case Operation.RemoveOperation _ -> {
                    if (containsKey){
                        var wLock = sizeSet.wLock();
                        var wlw = new LockWrapper(LockType.WRITE, op, wLock);
                        if (cmtx.addLock(wlw)) {
                            this.releaseReadLockIfHeld(Option.some(sizeSet), key, op);
                            wLock.lock();
                        }
                        cmtx.setModifying();
                    }
                }

                default -> throw new Error();// Should never happen
            }


            //Do the same thing for GET ops as well
            this.releaseReadLockIfHeld(set, key, GET);
        }

        void releaseReadLockIfHeld(Option<SynchronizedTxSet> txSet, K key, Operation op){
            txSet.map(set -> {
                        var rLock = set.rLock();
                        this.findExistingReadLock(op, rLock).ifPresent(lw -> {
                            lw.unlock();
                            cmtx.parent.heldLocks.remove(lw);
                        });
                        return Option.none();
                    });
        }

        Optional<LockWrapper> findExistingReadLock(Operation op, Lock lock){
            var glw = new LockWrapper(LockType.READ, op, lock);
            return cmtx.parent.heldLocks.stream()
                    .filter(lw -> lw.equals(glw))
                    .findFirst();
        }

    }
        record ChildTxAbortHandler<K, V>(ChildMapTransaction<K, V> cmtx) implements AbortHandler {
            @Override
            public void abortByConflictingSemantic() {
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
                    var op = cmtx.operation;
                    var lw = new LockWrapper(LockType.READ, op, lock);
                    if(cmtx.parent.heldLocks.remove(lw)) lw.unlock();
                }
            }


            public void abort(){
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

        record LockWrapper(LockType type, Operation op ,Lock lck){
            @Override
            public boolean equals(Object object) {
                if (object == null || getClass() != object.getClass()) return false;

                LockWrapper that = (LockWrapper) object;
                return op.equals(that.op) && type == that.type;
            }

            @Override
            public int hashCode() {
                return Objects.hash(type, op);
            }

            public void lock(){
                lck.lock();
            }

            public void unlock(){
                lck.unlock();
            }

        }

        public enum LockType{
            READ, WRITE
        }
    }




