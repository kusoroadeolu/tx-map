package io.github.kusoroadeolu.txmap.copyonwrite;

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
import java.util.concurrent.atomic.AtomicReference;

import static io.github.kusoroadeolu.txmap.Operation.ContainsKeyOperation.CONTAINS;
import static io.github.kusoroadeolu.txmap.Operation.GetOperation.GET;
import static io.github.kusoroadeolu.txmap.Operation.ModifyType.PUT;
import static io.github.kusoroadeolu.txmap.Operation.ModifyType.REMOVE;
import static io.github.kusoroadeolu.txmap.Operation.SizeOperation.SIZE;
import static io.github.kusoroadeolu.txmap.TransactionState.*;

public class CopyOnWriteTransactionalMap<K, V> implements TransactionalMap<K, V>{
    private final AtomicReference<ConcurrentMap<K, V>> map;

    public CopyOnWriteTransactionalMap() {
        this.map = new AtomicReference<>(new ConcurrentHashMap<>());
    }

    public MapTransaction<K, V> beginTx(){
        return new ParentTransaction<>(this);
    }


    public static class ParentTransaction<K, V> implements MapTransaction<K, V>{

        final List<ChildMapTransaction<K, V>> txs;
        TransactionState state;
        private final CopyOnWriteTransactionalMap<K, V> txMap;
        private Map<K, V> underlyingMap;
        boolean hasWrite;

        public ParentTransaction(CopyOnWriteTransactionalMap<K, V> txMap){
            this.txMap = txMap;
            this.txs = new ArrayList<>();
            this.state = SCHEDULED;
        }

        public FutureValue<Option<V>> put(K key, V value) {
            var op = new Operation.ModifyOperation<>(value, PUT);
            hasWrite = true;
            var future = new FutureValue<Option<V>>();
            var ctx = new ChildMapTransaction<>(this, op, Option.some(key), future);
            this.txs.add(ctx);
            return future;
        }

        @Override
        public FutureValue<Option<V>> remove(K key) {
            var op = new Operation.ModifyOperation<>(null, REMOVE);
            hasWrite = true;
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
        public boolean isCommitted() {
            return false;
        }

        @Override
        public void commit() {
            ConcurrentMap<K, V> prev;
            do {
                prev = txMap.map.get();
                underlyingMap = new HashMap<>(prev);
                txs.forEach(c -> c.commitHandler.validate());
            }while (hasWrite && !txMap.map.compareAndSet(prev, new ConcurrentHashMap<>(underlyingMap)));

            txs.forEach(ChildMapTransaction::commit);
            txs.clear();
            underlyingMap.clear();
            this.state = COMMITTED;
        }

        @Override
        public void abort() {
            txs.forEach(ChildMapTransaction::abort);
            txs.clear();
            underlyingMap.clear();
            this.state = ABORTED;
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


    public static class ChildMapTransaction<K, V> implements Transaction{
        final Option<K> key;
        final Operation operation;
        TransactionState state;
        ParentTransaction<K, V> parent;
        final CommitHandler commitHandler;
        private final AbortHandler abortHandler;
        private final FutureValue<?> future;
        Option<V> value;
        int size; //For size
        boolean contains; //For contains

        public ChildMapTransaction(ParentTransaction<K, V> parent, Operation operation, Option<K> key, FutureValue<?> future) {
            this.operation = operation;
            this.value = Option.none();
            this.parent = parent;
            this.state = SCHEDULED;
            this.commitHandler = new ChildTxCommitHandler<>(this);
            this.abortHandler = new ChildTxAbortHandler<>(this);
            this.key = key;
            this.future = future;
        }

        public ChildMapTransaction(ParentTransaction<K, V> parent, Operation operation, FutureValue<?> future){
            this(parent, operation, Option.none(), future);
        }


        @Override
        public void commit() {
            commitHandler.commit();
        }

        @Override
        public void abort() {
            abortHandler.abort();
        }

        @Override
        public Option<Transaction> parent() {
            return Option.some(parent);
        }

        @Override
        public TransactionState state() {
            return state;
        }
    }

    record ChildTxAbortHandler<K, V>(ChildMapTransaction<K, V> cmtx) implements AbortHandler{
        @Override
        public void abort() {
            cmtx.state = ABORTED;
            switch (cmtx.operation){
                case Operation.ModifyOperation<?> _, Operation.GetOperation _ ->  cmtx.future.complete(Option.none());

                case Operation.SizeOperation _-> cmtx.future.complete(-1);

                case Operation.ContainsKeyOperation _-> cmtx.future.complete(false);
            }
        }
    }


    record ChildTxCommitHandler<K, V>(ChildMapTransaction<K, V> cmtx) implements CommitHandler{
        @Override
        public void commit() {
            switch (cmtx.operation){
                case Operation.ModifyOperation<?> _, Operation.GetOperation _ ->  cmtx.future.complete(cmtx.value);

                case Operation.SizeOperation _-> cmtx.future.complete(cmtx.size);

                case Operation.ContainsKeyOperation _-> cmtx.future.complete(cmtx.contains);
            }
            cmtx.state = COMMITTED;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void validate() {
            cmtx.state = VALIDATED;
            var map = cmtx.parent.underlyingMap;
            switch (cmtx.operation){
                case Operation.ModifyOperation<?> op -> {
                    var key = cmtx.key.unwrap();
                    Option<V> prev;
                    if (op.type() == PUT) prev = Option.ofNullable(map.put(key, (V) op.element()));
                    else prev = Option.ofNullable(map.remove(key));
                    cmtx.value = prev;
                }

                case Operation.SizeOperation _-> cmtx.size = map.size();
                case Operation.GetOperation _-> {
                    var key = cmtx.key.unwrap();
                    cmtx.value = Option.ofNullable(map.get(key));
                }

                case Operation.ContainsKeyOperation _-> {
                    var key = cmtx.key.unwrap();
                    cmtx.contains = map.containsKey(key);
                }
            }
        }
    }
}
