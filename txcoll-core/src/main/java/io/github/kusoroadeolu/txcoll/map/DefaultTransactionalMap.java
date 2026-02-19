package io.github.kusoroadeolu.txcoll.map;

import io.github.kusoroadeolu.ferrous.option.None;
import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.ferrous.option.Some;
import io.github.kusoroadeolu.txcoll.*;
import io.github.kusoroadeolu.txcoll.handlers.AbortHandler;
import io.github.kusoroadeolu.txcoll.handlers.CommitHandler;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import static io.github.kusoroadeolu.txcoll.map.Operation.ContainsKeyOperation.CONTAINS;
import static io.github.kusoroadeolu.txcoll.map.Operation.GetOperation.GET;
import static io.github.kusoroadeolu.txcoll.map.Operation.SizeOperation.SIZE;

/*
* Happens before guarantees
* 1. The acquisition of read locks in a parent transaction before commit happens before the acquisition of write locks
* 2. The release of read locks of conflicting read semantics happens before the acquisition of those write locks
* 3. The validation of a transaction happens before its commit
* 4. The abortion of a read child transaction happens before its validation
* 5. The acquisition of a contains key write lock happens before the potential acquisition of a size lock
* 6. Write conflicting ops -> Contains key, (depending on the write type and contains key type, size might be conflicting), get
* 7. The release of read locks held by a transaction(on the same thread) of conflicting happens before the acquisition of the write lock to prevent deadlock issues
* */
public class DefaultTransactionalMap<K, V> implements TransactionalMap<K, V> {
    private final ConcurrentMap<K, V> map;

    //Shared state
    private final KeyToLockers<K> keyToLockers;
    private final SynchronizedTxSet sizeLockers;

    DefaultTransactionalMap(ConcurrentMap<K, V> map, KeyToLockers<K> keyToLockers, SynchronizedTxSet sizeLockers) {
        this.map = map;
        this.keyToLockers = keyToLockers;
        this.sizeLockers = sizeLockers;
    }

    public DefaultTransactionalMap(){
        this(new ConcurrentHashMap<>(), new KeyToLockers<>(), new SynchronizedTxSet());
    }

    @Override
    public MapTransaction<K, V> beginTx(){
        return new MapTransactionImpl<>(this);
    }

    static class MapTransactionImpl<K, V> implements MapTransaction<K, V> {
        //This transactional map
        final DefaultTransactionalMap<K, V> txMap;

        //Local fields
        final Map<K, Option<V>> storeBuf;
        final List<ChildMapTransaction<K, V>> txs;
        final Set<LockWrapper> heldLocks;
        int delta = 0;
        TransactionState state;
        private final AbortHandler abortHandler;
        private final CommitHandler commitHandler;


        public MapTransactionImpl(DefaultTransactionalMap<K, V> txMap){
            this.txMap = txMap;
            this.heldLocks = ConcurrentHashMap.newKeySet(); //In the case where two threads try to remove a iLock from this set
            this.storeBuf = new HashMap<>();
            this.txs = new ArrayList<>();
            this.state = TransactionState.NONE;
            this.abortHandler = new MapTxAbortHandler<>(this);
            this.commitHandler = new MapTxCommitHandler<>(this);
        }

        //WRITE OPS

        public FutureValue<Option<V>> put(K key, V value) {
            var op = new Operation.PutOperation<>(value);
            var future = new FutureValue<Option<V>>();
            var ctx = new ChildMapTransaction<>(this, op, Option.some(key), future);
            this.txs.add(ctx);
            return future;
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
             var lock = this.acquireReadLock(op, nullable);
             var ctx = new ChildMapTransaction<>(this, op, nullable, future, Option.some(lock));
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

         Lock acquireReadLock(Operation op, Option<K> key){
           return switch (key){
                case Some<K> s -> txMap.keyToLockers.getOrCreate(s.unwrap(), op)
                        .map(txSet -> txSet.uniqueAcquireReadLock(heldLocks, op, key.toNullable())) //Get the read iLock
                        .unwrap();

                case None<K> _ -> txMap.sizeLockers.uniqueAcquireReadLock(heldLocks, op, key.toNullable());
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

        @Override
        public boolean isCommitted() {
            return state == TransactionState.COMMITTED;
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
            var keyOption = cmtx.key;
            var storeBuf = cmtx.parent.storeBuf;
            switch (op) {
                case Operation.PutOperation<?> po -> {
                    V v = (V) po.value();
                    var prev = storeBuf.put(keyOption.unwrap(), Option.some(v));
                    var ulPrev = underlying.put(keyOption.unwrap(), v);
                    if(prev == null) prev = Option.ofNullable(ulPrev);
                    cmtx.state.compareAndSet(TransactionState.VALIDATED, TransactionState.COMMITTED);
                    cmtx.future.complete(prev);
                    if (cmtx.isModifying()) cmtx.parent.incrementDelta();
                }

                case Operation.RemoveOperation _ -> {
                    var option =  storeBuf.remove(keyOption.unwrap());
                    storeBuf.put(keyOption.unwrap(), Option.none()); //Put a none value
                    Option<V> prev;
                    if (option == null) {
                        prev = Option.ofNullable(underlying.remove(keyOption.unwrap()));
                    } else prev = (Option<V>) switch (option){
                            case Some<?> some -> some;
                            case None<?> _ -> Option.ofNullable(underlying.remove(keyOption.unwrap()));
                        };


                    cmtx.state.compareAndSet(TransactionState.VALIDATED, TransactionState.COMMITTED);
                    cmtx.future.complete(prev);
                    if (cmtx.isModifying()) cmtx.parent.decrementDelta();

                }

                case Operation.GetOperation _ -> {
                    var option = storeBuf.get(keyOption.unwrap());
                    Option<V> val;
                    if (option == null) {
                        val = Option.ofNullable(underlying.get(keyOption.unwrap()));
                    } else val = (Option<V>) switch (option){
                        case Some<?> some -> some;
                        case None<?> _ -> Option.ofNullable(underlying.get(keyOption.unwrap()));
                    };
                    cmtx.state.compareAndSet(TransactionState.VALIDATED, TransactionState.COMMITTED);
                    cmtx.future.complete(val);
                }

                case Operation.ContainsKeyOperation _ -> {
                    var opt = storeBuf.get(keyOption.unwrap());
                    boolean val = (opt instanceof Some<?>) || underlying.containsKey(keyOption.unwrap());
                    cmtx.state.compareAndSet(TransactionState.VALIDATED, TransactionState.COMMITTED);
                    cmtx.future.complete(val);
                }

                case Operation.SizeOperation _ -> {
                    int size = underlying.size();
                    cmtx.state.compareAndSet(TransactionState.VALIDATED, TransactionState.COMMITTED);
                    cmtx.future.complete(size);
                }

            }
        }

        public void validate(){
            this.validateOps(cmtx.operation);
        }

        void validateOps(Operation op){
            var txMap = cmtx.parent.txMap;
            var key = cmtx.key; //Write ops always have a key so this is safe
            switch (op){
                case Operation.PutOperation<?> _, Operation.RemoveOperation _ -> {
                    txMap.keyToLockers.getOrCreate(key.unwrap(), op)
                            .map(SynchronizedTxSet::wLock)
                            .flatMap(lock -> {
                                var wp = new LockWrapper(LockType.WRITE, op, lock);
                                if (cmtx.addLock(wp)) lock.lock();
                                return new Some<>(lock);
                            });
                    this.handleWriteOps(txMap, key.unwrap(), op);
                    cmtx.state.compareAndSet(TransactionState.NONE, TransactionState.VALIDATED);
                }

                //For read ops
                default -> {
                    if (!cmtx.state.compareAndSet(TransactionState.NONE, TransactionState.VALIDATED)){
                        if (cmtx.isAborted()) {
                            cmtx.state.compareAndSet(TransactionState.ABORTED, TransactionState.VALIDATED);
                            cmtx.parent.acquireReadLock(cmtx.operation, cmtx.key); //Set to validated first to prevent livelock issues
                        }
                    }
                } //We want to CAS initially, if we cant, then it means we're aborted
            }
        }


        void handleWriteOps(DefaultTransactionalMap<K, V> txMap, K key, Operation op){
            //Ensure we only lock once, since a tx is basically only on a single thread, we cant really get deadlocks, but we want to ensure we release all locks
            //Then we want to grab to writeLocks for the contains operation, we want to check if the underlying map contains the key, so we can grab the size lock as well
            //Then lock the write lock to prevent a situation where we cant use the write lock cuz we have the read iLock
            var containsSet = txMap.keyToLockers.getOrCreate(key, CONTAINS);
            this.releaseReadLockIfHeld(containsSet, CONTAINS);
            containsSet.ifSome(txSet -> txSet.abortAll(cmtx));

            //Now that we have the iLock for contains key , we can check the underlying map to see if we should obtain the size iLock too
            boolean containsKey = txMap.map.containsKey(key);
            var sizeSet = txMap.sizeLockers;
             switch (op){
                case Operation.PutOperation<?> _ -> {
                    if (!containsKey){
                        this.releaseReadLockIfHeld(Option.some(sizeSet), op);
                        sizeSet.abortAll(cmtx);
                        cmtx.setModifying();
                    }
                }

                case Operation.RemoveOperation _ -> {
                    if (containsKey){
                        this.releaseReadLockIfHeld(Option.some(sizeSet), op);
                        sizeSet.abortAll(cmtx);
                        cmtx.setModifying();
                    }
                }

                default -> throw new Error();// Should never happen
            }


            //Do the same thing for GET ops as well
            var getSet = txMap.keyToLockers.getOrCreate(key, GET);
            this.releaseReadLockIfHeld(getSet, GET);
            getSet.ifSome(txSet -> txSet.abortAll(cmtx));
        }




        void releaseReadLockIfHeld(Option<SynchronizedTxSet> txSet, Operation op){
            txSet.map(_ -> {
                        this.findExistingReadLock(op).ifPresent(lw -> {
                            lw.unlock();
                            cmtx.parent.heldLocks.remove(lw);
                        });
                        return Option.none();
                    });
        }

        Optional<LockWrapper> findExistingReadLock(Operation op){
            var glw = new LockWrapper(LockType.READ, op, null);
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
                     cmtx.lock.ifSome(lock -> {
                        var op = cmtx.operation;
                        K key;
                        if (op == SIZE) key = null;
                        else key = cmtx.key.unwrap();

                        var lw = new LockWrapper(LockType.READ, op, lock);
                        if(cmtx.parent.heldLocks.remove(lw)) lw.unlock();
                    });

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
                        txMap.keyToLockers.getOrCreate(key.unwrap(), op)
                                .ifSome(s -> s.remove(cmtx));
                    }
                }

            }
        }

        record LockWrapper(LockType type, Operation op ,Lock iLock){
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




