package io.github.kusoroadeolu.txcoll.map;

import io.github.kusoroadeolu.ferrous.option.None;
import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.ferrous.option.Some;
import io.github.kusoroadeolu.txcoll.*;
import io.github.kusoroadeolu.txcoll.handlers.AbortHandler;
import io.github.kusoroadeolu.txcoll.handlers.CommitHandler;
import io.github.kusoroadeolu.txcoll.map.Operation.ModifyOperation;
import io.github.kusoroadeolu.txcoll.map.Operation.ModifyType;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import static io.github.kusoroadeolu.txcoll.map.Operation.ContainsKeyOperation.CONTAINS;
import static io.github.kusoroadeolu.txcoll.map.Operation.DEFAULT_MODIFY_OP;
import static io.github.kusoroadeolu.txcoll.map.Operation.GetOperation.GET;
import static io.github.kusoroadeolu.txcoll.map.Operation.ModifyType.PUT;
import static io.github.kusoroadeolu.txcoll.map.Operation.SizeOperation.SIZE;

/*
* Happens before guarantees
* 1. The acquisition of read locks happens before the acquisition of write locks
* 2. The ordering of write locks happens before their acquisition
* 3. The validation of a transaction happens before its commit
* 4. The abort of a read child transaction happens before its validation
* 5. The acquisition of a contains key write lock happens before the potential acquisition of a size lock
* 6. Write conflicting ops -> Contains key, (depending on the write type and contains key type, size might be conflicting), get
* 7. The release of read locks held by a transaction(on the same thread) of conflicting happens before the acquisition of the write lock to prevent deadlock issues
* */
public class DefaultTransactionalMap<K, V> implements TransactionalMap<K, V> {
    private final ConcurrentMap<K, V> map;

    //Shared state
    private final KeyToLockers<K> keyToLockers;
    private final GuardedTxSet sizeLockers;

    DefaultTransactionalMap(ConcurrentMap<K, V> map, KeyToLockers<K> keyToLockers, GuardedTxSet sizeLockers) {
        this.map = map;
        this.keyToLockers = keyToLockers;
        this.sizeLockers = sizeLockers;
    }

    public DefaultTransactionalMap(){
        this(new ConcurrentHashMap<>(), new KeyToLockers<>(), new GuardedTxSet());
    }

    @Override
    public MapTransaction<K, V> beginTx(){
        return new MapTransactionImpl<>(this);
    }

    static class MapTransactionImpl<K, V> implements MapTransaction<K, V> {
        //This transactional map
        final DefaultTransactionalMap<K, V> txMap;

        //Local fields
        final List<ChildMapTransaction<K, V>> txs;
        final Set<LockWrapper> heldLocks;
        TransactionState state;
        private final AbortHandler abortHandler;
        private final CommitHandler commitHandler;


        public MapTransactionImpl(DefaultTransactionalMap<K, V> txMap){
            this.txMap = txMap;
            this.heldLocks = ConcurrentHashMap.newKeySet(); //In the case where two threads try to remove a iLock from this set
            this.txs = new ArrayList<>();
            this.state = TransactionState.NONE;
            this.abortHandler = new MapTxAbortHandler<>(this);
            this.commitHandler = new MapTxCommitHandler<>(this);
        }

        //WRITE OPS
        public FutureValue<Option<V>> put(K key, V value) {
            var op = new ModifyOperation<>(value, PUT);
            var future = new FutureValue<Option<V>>();
            var ctx = new ChildMapTransaction<>(this, op, Option.some(key), future);
            this.txs.add(ctx);
            return future;
        }

        @Override
        public FutureValue<Option<V>> remove(K key) {
            var future = new FutureValue<Option<V>>();
            this.txs.add(new ChildMapTransaction<>(this, DEFAULT_MODIFY_OP, Option.some(key), future));
            return future;
        }

         // READ OPS
         FutureValue<?> registerReadOp(@Nullable K key, Operation op, FutureValue<?> future){
             var nullable = Option.ofNullable(key);
             this.acquireReadLock(op, nullable);
             var ctx = new ChildMapTransaction<>(this, op, nullable, future);
             this.txs.add(ctx);
             switch (nullable) {
                 case Some<?> _ -> txMap.keyToLockers.put(key, op, ctx);
                 case None<?> _ -> txMap.sizeLockers.put(ctx);
             }
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
             var future = new FutureValue<Integer>();
             return (FutureValue<Integer>) registerReadOp(null, SIZE, future);
         }

         void acquireReadLock(Operation op, Option<K> key){
            switch (key){
                case Some<K> s -> txMap.keyToLockers.getOrCreate(s.unwrap(), op)
                        .ifSome(txSet -> txSet.uniqueAcquireReadLock(heldLocks, op));

                case None<K> _ -> txMap.sizeLockers.uniqueAcquireReadLock(heldLocks, op);
            };
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

        @Override
        public boolean isCommitted() {
            return state == TransactionState.COMMITTED;
        }

        public void abort() {
            abortHandler.abort();
         }

         void clearAll(){
             heldLocks.clear();
             txs.clear();
         }
     }


    record MapTxAbortHandler<K, V>(MapTransactionImpl<K, V> tx) implements AbortHandler{

        @Override
        public void abort() {
            tx.txs.forEach(ChildMapTransaction::abort);
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
                switch (cmtx.operation){
                    case Operation.SizeOperation _ -> tx.txMap.sizeLockers.remove(cmtx);
                    default -> {
                        var op = cmtx.operation;
                        var key = cmtx.key;
                        tx.txMap.keyToLockers.getOrCreate(key.unwrap(), op)
                                .ifSome(s -> s.remove(cmtx));
                    }
                }
            });
            tx.clearAll();
            tx.state = TransactionState.COMMITTED;
        }



        public void validate() {
            tx.txs.forEach(ChildMapTransaction::validate);
            tx.state = TransactionState.VALIDATED;
        }
    }

    static class ChildMapTransaction<K, V> implements Transaction {
            private final MapTransactionImpl<K, V> parent;
            final Option<K> key;
            final Operation operation;
            TransactionState state;
            private final CommitHandler commitHandler;
            private final AbortHandler abortHandler;
            private final FutureValue<?> future;

        public ChildMapTransaction(MapTransactionImpl<K, V> parent, Operation operation, Option<K> key, FutureValue<?> future) {
            this.operation = operation;
            this.parent = parent;
            this.state = TransactionState.NONE;
            this.commitHandler = new ChildTxCommitHandler<>(this);
            this.abortHandler = new ChildTxAbortHandler<>(this);
            this.key = key;
            this.future = future;
        }


        public Option<Transaction> parent() {
            return Option.some(parent);
        }

        public void validate(){
            commitHandler.validate();
        }

        public void commit() {
            commitHandler.commit();
        }

        //Only read ops can be aborted so we'll implement specifically for that
        public void abort() {
            this.abortHandler.abort();
        }


        public TransactionState state() {
            return state;
        }
    }


    record ChildTxCommitHandler<K, V>(ChildMapTransaction<K, V> cmtx) implements CommitHandler {

        @SuppressWarnings("unchecked")
        public void commit() {
            var op = cmtx.operation;
            var underlying = cmtx.parent.txMap.map;
            var keyOption = cmtx.key;
            Option<V> prevOpt;
            switch (op) {
                case ModifyOperation<?> mo -> {
                    var key = keyOption.unwrap();
                    if (mo.type() == PUT){
                        V v = (V) mo.element();
                        V prev = underlying.put(key, v);
                        prevOpt = Option.ofNullable(prev);
                    }else{
                        prevOpt = Option.ofNullable(underlying.remove(key));
                    }
                    cmtx.state = TransactionState.COMMITTED;
                    cmtx.future.complete(prevOpt);
                }


                case Operation.GetOperation _ -> {
                    var v = underlying.get(keyOption.unwrap());
                    Option<V> opt = Option.ofNullable(v);
                    cmtx.state = TransactionState.COMMITTED;
                    cmtx.future.complete(opt);
                }

                case Operation.ContainsKeyOperation _ -> {
                    boolean contains = underlying.containsKey(keyOption.unwrap());
                    cmtx.state = TransactionState.COMMITTED;
                    cmtx.future.complete(contains);
                }

                case Operation.SizeOperation _ -> {
                    int size = underlying.size();
                    cmtx.state = TransactionState.COMMITTED;
                    cmtx.future.complete(size);
                }

            }
        }

        public void validate(){
            this.validateOps(cmtx.operation);
        }

        public void orderThenAcquireKeys(Operation op){
            var tx = cmtx.parent;
            tx.txs.stream()
                    .filter(c -> c.operation instanceof Operation.ModifyOperation<?>)
                    .map(c -> c.key.unwrap())
                    .distinct()
                    .sorted(Comparator.comparingInt(System::identityHashCode))
                    .forEach(
                            key -> tx.txMap.keyToLockers.getOrCreate(key, op)
                            .map(GuardedTxSet::writeLock)
                            .map(lock -> new LockWrapper(LockType.WRITE,  DEFAULT_MODIFY_OP, lock))
                            .filter(tx.heldLocks::add)
                            .ifSome(LockWrapper::lock)
                    );
        }

        void validateOps(Operation op){
            var txMap = cmtx.parent.txMap;
            var key = cmtx.key; //Write ops always have a key so this is safe
            switch (op){
                case ModifyOperation<?> mo -> {
                    this.orderThenAcquireKeys(mo);
                    this.handleWriteOps(txMap, key.unwrap(), mo);
                    cmtx.state = TransactionState.VALIDATED;
                }

                //For read ops
                default -> cmtx.state = TransactionState.VALIDATED;
            }
        }


        void handleWriteOps(DefaultTransactionalMap<K, V> txMap, K key, ModifyOperation<?> op){
            //Take the get lock first
            var getSet = txMap.keyToLockers.getOrCreate(key, GET);
            this.releaseReadLockIfHeld(getSet, GET);

            //Ensure we only lock once, since a tx is basically only on a single thread, we cant really get deadlocks, but we want to ensure we release all locks
            //Then we want to grab to writeLocks for the contains operation, we want to check if the underlying map contains the key, so we can grab the size lock as well
            //Then lock the write lock to prevent a situation where we cant use the write lock cuz we have the read iLock
            var containsSet = txMap.keyToLockers.getOrCreate(key, CONTAINS);
            this.releaseReadLockIfHeld(containsSet, CONTAINS);

            //Now that we have the iLock for contains key , we can check the underlying map to see if we should obtain the size iLock too
            boolean containsKey = txMap.map.containsKey(key);
            var sizeSet = txMap.sizeLockers;
             switch (op.type()){
                 case PUT -> {
                    if (!containsKey){
                        this.releaseReadLockIfHeld(Option.some(sizeSet), op);
                    }
                }

                 case REMOVE -> {
                    if (containsKey){
                        this.releaseReadLockIfHeld(Option.some(sizeSet), op);
                    }
                }

                default -> throw new Error();// Should never happen
            }
        }

        void releaseReadLockIfHeld(Option<GuardedTxSet> txSet, Operation op){
            txSet.map(_ -> {
                        this.findExistingReadLock(op)
                                .ifPresent(lw -> {
                            lw.unlock();
                            cmtx.parent.heldLocks.remove(lw);
                        });
                        return Option.none();
                    });
        }

        Optional<LockWrapper> findExistingReadLock(Operation op){
            var glw = new LockWrapper(LockType.READ, op, null);
            return cmtx.parent.heldLocks
                    .stream()
                    .filter(lw -> lw.equals(glw))
                    .findFirst();
        }

    }
        record ChildTxAbortHandler<K, V>(ChildMapTransaction<K, V> cmtx) implements AbortHandler {
            public void abort(){
                var txMap = cmtx.parent.txMap;
                cmtx.state = TransactionState.ABORTED;
                switch (cmtx.operation){
                    case Operation.SizeOperation _ -> txMap.sizeLockers.remove(cmtx);
                    default -> {
                        var op = cmtx.operation;
                        var key = cmtx.key;
                        txMap.keyToLockers.getOrCreate(key.unwrap(), op)
                                .ifSome(s -> s.remove(cmtx));
                    }
                }

            }
        }

        record LockWrapper(LockType type, Operation op ,Lock iLock){
            public void lock(){
                iLock.lock();
            }

            public void unlock(){
                iLock.unlock();
            }

        }

        public enum LockType{
            READ, WRITE
        }
    }




