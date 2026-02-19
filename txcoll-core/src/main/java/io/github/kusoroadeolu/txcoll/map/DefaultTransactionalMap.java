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
import java.util.concurrent.locks.Lock;

import static io.github.kusoroadeolu.txcoll.TransactionState.*;
import static io.github.kusoroadeolu.txcoll.map.Operation.ContainsKeyOperation.CONTAINS;
import static io.github.kusoroadeolu.txcoll.map.Operation.GetOperation.GET;
import static io.github.kusoroadeolu.txcoll.map.Operation.ModifyType.PUT;
import static io.github.kusoroadeolu.txcoll.map.Operation.ModifyType.REMOVE;
import static io.github.kusoroadeolu.txcoll.map.Operation.SizeOperation.SIZE;

/*
* Happens before guarantees
* 1. Volatile reads of the txSet status establishes visibility of prior status writes before acquiring write locks.
* 2. The validation of a transaction happens before its commit. This guarantee is upheld by deterministic ordering
* 3. During validation, the ordering of modify locks happens before their acquisition, to prevent deadlocks. This guarantee is upheld by deterministic ordering
 * 4. The acquisition of a contains key lock happens before the potential acquisition of a size lock. This guarantee is upheld by deterministic ordering
 * 5. The acquisition of modify locks happens before the acquisition of read locks (in this order,MODIFY, GET, CONTAINS, SIZE) . This guarantee is upheld by deterministic ordering
 * 6. During commits, the commits of child transactions happens before the release of all held locks. This guarantee is upheld by deterministic ordering
 * 7. During commits, the release of all held locks happens before the removal of open nested child transactions. This guarantee is upheld by deterministic ordering
 * 8. During aborts, the release of all held locks happens before the removal of open nested child transactions. This guarantee is upheld by deterministic ordering
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
        final Set<Lock> heldLocks;
        TransactionState state;
        private final AbortHandler abortHandler;
        private final CommitHandler commitHandler;
        boolean hasAborted;


        public MapTransactionImpl(DefaultTransactionalMap<K, V> txMap){
            this.txMap = txMap;
            this.heldLocks = new HashSet<>();
            this.storeBuf = new HashMap<>();
            this.txs = new ArrayList<>();
            this.state = SCHEDULED;
            this.abortHandler = new MapTxAbortHandler<>(this);
            this.commitHandler = new MapTxCommitHandler<>(this);
        }

        //WRITE OPS

        public FutureValue<Option<V>> put(K key, V value) {
            var op = new Operation.ModifyOperation<>(value, PUT);
            var future = new FutureValue<Option<V>>();
            var ctx = new ChildMapTransaction<>(this, op, Option.some(key), future);
            this.txs.add(ctx);
            return future;
        }

        @Override
        public FutureValue<Option<V>> remove(K key) {
            var op = new Operation.ModifyOperation<>(null, REMOVE);
            var future = new FutureValue<Option<V>>();
            this.txs.add(new ChildMapTransaction<>(this, op, Option.some(key), future));
            return future;
        }

         // READ OPS
        @SuppressWarnings("unchecked")
         FutureValue<?> registerReadOp(@Nullable K key, Operation op, FutureValue<?> future){
             var nullable = Option.ofNullable(key);
             ChildMapTransaction<K, V> ctx;

             switch (nullable) {
                 case Some<?> s -> {
                     var set = txMap.keyToLockers.getOrCreate(key, op).unwrap();
                     ctx = new ChildMapTransaction<>(this, op,(Option<K>) s, future);
                     this.txs.add(ctx);
                     set.put(ctx);
                 }
                 case None<?> _ -> {
                     ctx = new ChildMapTransaction<>(this, op, future);
                     this.txs.add(ctx);
                     txMap.sizeLockers.put(ctx);
                 }
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
            if (!isCommitted()) abortHandler.abort();
         }

         void clearAll(){
             storeBuf.clear();
             heldLocks.clear();
             txs.clear();
         }
     }


    record MapTxAbortHandler<K, V>(MapTransactionImpl<K, V> tx) implements AbortHandler{

        @Override
        public void abort() {
            tx.txs.forEach(ChildMapTransaction::abort);
            tx.heldLocks.forEach(Lock::unlock);
            tx.state = TransactionState.ABORTED;
            tx.clearAll();
        }
    }

    record MapTxCommitHandler<K, V>(MapTransactionImpl<K, V> tx) implements CommitHandler{
        @Override
        public void commit() {
            if (tx.hasAborted) { //Check if we have an aborted tx
                tx.abort();
                return;
            }
            tx.txs.forEach(ChildMapTransaction::commit);
            tx.heldLocks.forEach(Lock::unlock); //Then unlock all locks
            tx.txs.forEach(cmtx -> {
                switch (cmtx.operation){
                    case Operation.SizeOperation _ -> {
                        var set = tx.txMap.sizeLockers;
                        set.remove(cmtx);
                        set.decrementReaderCount();
                    }
                    default -> {
                        var op = cmtx.operation;
                        var key = cmtx.key;
                        tx.txMap.keyToLockers.getOrCreate(key.unwrap(), op)
                                .inspect(set -> set.remove(cmtx))
                                .inspect(SynchronizedTxSet::decrementReaderCount);
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
            TransactionState state;
            private boolean incrementedCount;
            private final CommitHandler commitHandler;
            private final AbortHandler abortHandler;
            private final FutureValue<?> future;

        public ChildMapTransaction(MapTransactionImpl<K, V> parent, Operation operation, Option<K> key, FutureValue<?> future, Option<Lock> lock) {
            this.operation = operation;
            this.lock = lock;
            this.parent = parent;
            this.state = SCHEDULED;
            this.commitHandler = new ChildTxCommitHandler<>(this);
            this.abortHandler = new ChildTxAbortHandler<>(this);
            this.key = key;
            this.future = future;
        }

        public ChildMapTransaction(MapTransactionImpl<K, V> parent, Operation operation, Option<K> key, FutureValue<?> future){
            this(parent, operation, key, future, Option.none());
        }

        public ChildMapTransaction(MapTransactionImpl<K, V> parent, Operation operation, FutureValue<?> future){
            this(parent, operation, Option.none(), future, Option.none());
        }


        public Option<Transaction> parent() {
            return Option.some(parent);
        }

        public boolean addLock(Lock lock){
           return this.parent.heldLocks.add(lock);
        }

        public void validate(){
            commitHandler.validate();
        }

        public void commit() {
            commitHandler.commit();
        }

        public void abort(){
            this.abortHandler.abort();
        }

        public TransactionState state() {
            return state;
        }

        public void setIncrementedCount(){
            incrementedCount = true;
        }

        public boolean hasIncrementedCount(){
            return incrementedCount;
        }

        public boolean isAborted(){
            return state == ABORTED;
        }
    }


    record ChildTxCommitHandler<K, V>(ChildMapTransaction<K, V> cmtx) implements CommitHandler {

        @SuppressWarnings("unchecked")
        public void commit() {
            var op = cmtx.operation;
            var underlying = cmtx.parent.txMap.map;
            var keyOption = cmtx.key;
            var storeBuf = cmtx.parent.storeBuf;
            Option<V> prev;
            switch (op) {
                case Operation.ModifyOperation<?> mo -> {
                    if (mo.type() == PUT){
                        V v = (V) mo.element();
                        prev = storeBuf.put(keyOption.unwrap(), Option.some(v));
                        var ulPrev = underlying.put(keyOption.unwrap(), v);
                        if(prev == null) prev = Option.ofNullable(ulPrev);

                    }else {
                        var option =  storeBuf.remove(keyOption.unwrap());
                        storeBuf.put(keyOption.unwrap(), Option.none()); //Put a none value
                        if (option == null) prev = Option.ofNullable(underlying.remove(keyOption.unwrap()));
                        else prev = (Option<V>) switch (option){
                            case Some<?> some -> some;
                            case None<?> _ -> Option.ofNullable(underlying.remove(keyOption.unwrap()));
                        };
                    }

                    cmtx.state = TransactionState.COMMITTED;
                    cmtx.future.complete(prev);
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
                    cmtx.state = TransactionState.COMMITTED;
                    cmtx.future.complete(val);
                }

                case Operation.ContainsKeyOperation _ -> {
                    var opt = storeBuf.get(keyOption.unwrap());
                    boolean val = (opt instanceof Some<?>) || underlying.containsKey(keyOption.unwrap());
                    cmtx.state = TransactionState.COMMITTED;
                    cmtx.future.complete(val);
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
                    .forEach(key -> tx.txMap.keyToLockers.getOrCreate(key, op)
                                .map(SynchronizedTxSet::getLock)
                                .filter(tx.heldLocks::add)
                                .ifSome(Lock::lock));
        }

        void validateOps(Operation op){
            var txMap = cmtx.parent.txMap;
            this.orderThenAcquireKeys(op);

            //Write ops always have a key so this is safe
            switch (op){
                case Operation.ModifyOperation<?> _ ->  {
                    var key = cmtx.key.unwrap();
                    this.handleWriteOps(txMap, key, op);
                    cmtx.state = TransactionState.VALIDATED;
                }

                //For read ops
                default -> {
                    //Check the value
                    var set = cmtx.key.isNone() ? txMap.sizeLockers : txMap.keyToLockers.getOrCreate(cmtx.key.unwrap(), op).unwrap();
                    if (set.isHeld()) {
                        try {
                            set.latch().await();
                        } catch (InterruptedException _) {
                            Thread.currentThread().interrupt();
                            cmtx.state = TransactionState.ABORTED;
                            cmtx.parent.hasAborted = true;
                        }
                    }

                    if (!cmtx.isAborted()){
                        set.incrementReaderCount();
                        cmtx.setIncrementedCount();
                        cmtx.state = TransactionState.VALIDATED;
                    }

                }
            }
        }


        void handleWriteOps(DefaultTransactionalMap<K, V> txMap, K key, Operation op){
            //Ensure we only lock once, since a tx is basically only on a single thread, we cant really get deadlocks, but we want to ensure we release all locks
            //Then we want to grab to writeLocks for the contains operation, we want to check if the underlying map contains the key, so we can grab the size lock as well
            //Then lock the write lock to prevent a situation where we cant use the write lock cuz we have the read iLock

            var heldLocks =  cmtx.parent.heldLocks;

            //Do the same thing for GET ops as well
            txMap.keyToLockers.getOrCreate(key, GET)
                    .ifSome(txSet -> {
                        var lock = txSet.getLock();
                        txSet.lockAndIncrement(s -> s.contains(lock), heldLocks);
                    });


            txMap.keyToLockers.getOrCreate(key, CONTAINS)
                    .ifSome(txSet -> {
                        var lock = txSet.getLock();
                        txSet.lockAndIncrement(s -> s.contains(lock), heldLocks);
                    });



            //Now that we have the iLock for contains key , we can check the underlying map to see if we should obtain the size iLock too
            boolean containsKey = txMap.map.containsKey(key);
            var sizeSet = txMap.sizeLockers;
            var opType = ((Operation.ModifyOperation<?>) op).type();
             switch (opType){
                 case PUT -> {
                     if (!containsKey) sizeSet.lockAndIncrement(s -> s.contains(sizeSet.getLock()), heldLocks);
                 }

                 case REMOVE -> {
                    if (containsKey) sizeSet.lockAndIncrement(s -> s.contains(sizeSet.getLock()), heldLocks);

                }

                default -> throw new Error();// Should never happen
            }
        }


    }
        record ChildTxAbortHandler<K, V>(ChildMapTransaction<K, V> cmtx) implements AbortHandler {
            public void abort(){
                var txMap = cmtx.parent.txMap;
                cmtx.state = ABORTED;
                switch (cmtx.operation){
                    case Operation.SizeOperation _ -> {
                        var set = txMap.sizeLockers;
                        set.remove(cmtx);
                        if(cmtx.hasIncrementedCount()) set.decrementReaderCount();
                    }

                    default -> {
                        var op = cmtx.operation;
                        var key = cmtx.key;
                        txMap.keyToLockers.getOrCreate(key.unwrap(), op)
                                .inspect(set -> set.remove(cmtx))
                                .inspect(SynchronizedTxSet::decrementReaderCount);
                    }
                }

            }
        }
    }




