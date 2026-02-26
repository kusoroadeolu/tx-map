package io.github.kusoroadeolu.txmap.uncommitted;

import io.github.kusoroadeolu.ferrous.option.None;
import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.ferrous.option.Some;
import io.github.kusoroadeolu.txmap.*;
import io.github.kusoroadeolu.txmap.handlers.AbortHandler;
import io.github.kusoroadeolu.txmap.handlers.CommitHandler;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;

import static io.github.kusoroadeolu.txmap.TransactionState.*;
import static io.github.kusoroadeolu.txmap.Operation.ContainsKeyOperation.CONTAINS;
import static io.github.kusoroadeolu.txmap.Operation.GetOperation.GET;
import static io.github.kusoroadeolu.txmap.Operation.ModifyType.PUT;
import static io.github.kusoroadeolu.txmap.Operation.ModifyType.REMOVE;
import static io.github.kusoroadeolu.txmap.Operation.SizeOperation.SIZE;

/*
* Happens before guarantees
* 1. The validation of a transaction happens before its commit. This guarantee is upheld by deterministic ordering
* 2. During validation, the ordering of modify locks happens before their acquisition, to prevent deadlocks. This guarantee is upheld by deterministic ordering
 * 3. The acquisition of a contains key lock happens before the potential acquisition of a size lock. This guarantee is upheld by deterministic ordering
 * 4. During commits, the commits of child transactions happens before the release of all held locks. This guarantee is upheld by deterministic ordering
 * 5. The read of a size of the map in a transaction happens before the commit of the transaction
 * 6. The read of a get/contains keys of the map in a transaction happens before the commit of the transaction, subsequent gets/contains retrieve values from their local transaction's store buffer
 * */
//DIRTY READ Isolation level
public class ReadUncommittedTransactionalMap<K, V> implements TransactionalMap<K, V> {
    private final LockHolder<K, V> holder;
    private final ConcurrentMap<K, V> map;

    public ReadUncommittedTransactionalMap() {
        this.holder = new LockHolder<>();
        this.map = new ConcurrentHashMap<>();
    }

    @Override
    public MapTransaction<K, V> beginTx(){
        return new MapTransactionImpl<>(this);
    }

    static class MapTransactionImpl<K, V> implements MapTransaction<K, V> {
        //This transactional map
        final ReadUncommittedTransactionalMap<K, V> txMap;


        //Local fields
        final List<ChildMapTransaction<K, V>> txs;
        final Set<Lock> heldLocks;
        TransactionState state;
        private final AbortHandler abortHandler;
        private final CommitHandler commitHandler;
        int size, delta;
        private final Map<K, V> storeBuf;


        public MapTransactionImpl(ReadUncommittedTransactionalMap<K, V> txMap){
            this.txMap = txMap;
            this.storeBuf = new HashMap<>();
            this.heldLocks = new HashSet<>();
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
                     ctx = new ChildMapTransaction<>(this, op,(Option<K>) s, future);
                     this.txs.add(ctx);
                 }
                 case None<?> _ -> {
                     ctx = new ChildMapTransaction<>(this, op, future);
                     this.txs.add(ctx);
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
            tx.size = tx.txMap.map.size();
            tx.txs.forEach(c -> {

                if (c.operation != SIZE ){
                    var key = c.key.unwrap();
                    tx.storeBuf.put(key, tx.txMap.map.get(key));
                }
            });
            tx.txs.forEach(ChildMapTransaction::commit);
            tx.heldLocks.forEach(Lock::unlock); //Then unlock all locks
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
            this.state = SCHEDULED;
            this.commitHandler = new ChildTxCommitHandler<>(this);
            this.abortHandler = new ChildTxAbortHandler<>(this);
            this.key = key;
            this.future = future;
        }

        public ChildMapTransaction(MapTransactionImpl<K, V> parent, Operation operation, FutureValue<?> future){
            this(parent, operation, Option.none(), future);
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

        public void abort(){
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
            var parent = cmtx.parent;
            var underlying = parent.txMap.map;
            var buf = parent.storeBuf;
            var keyOption = cmtx.key;
            Option<V> prevOpt;


            switch (op) {
                case Operation.ModifyOperation<?> mo -> {
                    var key = keyOption.unwrap();
                    if (mo.type() == PUT){
                        V v = (V) mo.element();
                        V prev = underlying.put(key, v);
                        buf.put(key, v);
                        prevOpt = Option.ofNullable(prev);
                        if (prevOpt.isNone()) cmtx.parent.delta++;
                    }else{
                        prevOpt = Option.ofNullable(underlying.remove(key));
                        buf.remove(key);
                        if (prevOpt.isSome()) cmtx.parent.delta--;

                    }
                    cmtx.state = TransactionState.COMMITTED;
                    cmtx.future.complete(prevOpt);
                }

                case Operation.GetOperation _ -> {
                    var key = keyOption.unwrap();
                    var v = buf.get(key);
                    Option<V> opt = Option.ofNullable(v);
                    if (opt.isNone()) opt = Option.ofNullable(underlying.get(key)); //Try a dirty read
                    cmtx.state = TransactionState.COMMITTED;
                    cmtx.future.complete(opt);
                }

                case Operation.ContainsKeyOperation _ -> {
                    boolean contains = buf.containsKey(keyOption.unwrap());
                    cmtx.state = TransactionState.COMMITTED;
                    cmtx.future.complete(contains);
                }

                case Operation.SizeOperation _ -> {
                    int size = cmtx.parent.size + cmtx.parent.delta;
                    cmtx.state = TransactionState.COMMITTED;
                    cmtx.future.complete(size);
                }

            }
        }

        public void validate(){
            this.validateOps();
        }

        public void orderKeysThenAcquireLocks(){
            var tx = cmtx.parent;
            tx.txs.stream()
                    .filter(c -> c.operation instanceof Operation.ModifyOperation<?>)
                    .map(c -> c.key.unwrap())
                    .distinct()
                    .sorted(Comparator.comparingInt(System::identityHashCode))
                    .forEach(key -> Option.some(tx.txMap.holder.getLock(key))
                            .filter(tx.heldLocks::add)
                            .ifSome(Lock::lock));
        }

        void validateOps(){
            this.orderKeysThenAcquireLocks();
            cmtx.state = VALIDATED;
        }


    }
        record ChildTxAbortHandler<K, V>(ChildMapTransaction<K, V> cmtx) implements AbortHandler {
            public void abort(){
                cmtx.state = ABORTED;
                }

            }
    }





