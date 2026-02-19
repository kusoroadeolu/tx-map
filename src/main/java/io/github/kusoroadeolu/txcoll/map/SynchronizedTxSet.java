package io.github.kusoroadeolu.txcoll.map;

import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.txcoll.Transaction;
import io.github.kusoroadeolu.txcoll.map.TransactionalMap.ChildMapTransaction;
import io.github.kusoroadeolu.txcoll.map.TransactionalMap.LockWrapper;
import io.github.kusoroadeolu.txcoll.map.TransactionalMap.MapTransactionImpl;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;

//Happens before edges
/*
* A abort of transactions in the set by a transaction happens before the acquisition of the write lock by the transaction that aborted
* */
class SynchronizedTxSet {
    private final Set<Transaction> txSet;
    private final Lock rLock;
    private final ReentrantReadWriteLock rwLock;
    private final Lock wLock;
    private static final ILockBiFunction BI_FUNCTION = new ILockBiFunction();

    public SynchronizedTxSet(){
        this.txSet = ConcurrentHashMap.newKeySet();
        this.rwLock = new ReentrantReadWriteLock();
        this.rLock = rwLock.readLock();
        this.wLock = rwLock.writeLock();
    }

    //Ensure only one tx can abort at a time, and only the tx that aborted can take the lock
    public void abortAll(ChildMapTransaction<?, ?> aborter){
        synchronized (this){
           txSet.stream().filter(tx -> !tx.parent().equals(aborter.parent())).forEach(Transaction::abort);
           aborter.parent().map(p -> ((MapTransactionImpl<?, ?>) p).heldLocks)
                    .ifSome(slw -> BI_FUNCTION.apply(slw , new LockWrapper(TransactionalMap.LockType.WRITE, aborter.operation, null)));
        }
    }

    public boolean put(Transaction tx){
        return txSet.add(tx);
    }

    public void remove(Transaction tx){
        txSet.remove(tx);
    }

    public Lock rLock(Set<LockWrapper> heldLocks, Operation op){
        synchronized (this){ //In the case a value already added to the map but not aborted trys to acquire a lock while writer is aborting
           return Option.ofNullable(heldLocks)
                    .map(slw -> BI_FUNCTION.apply(slw, new LockWrapper(TransactionalMap.LockType.READ, op, null)))
                    .unwrap();

        }
    }


    //This is only held by write transactions, for read transactions that
    public Lock wLock(){
        return this.wLock;
    }


    public boolean writeLockHeldByCurrentThread(){
        return rwLock.isWriteLockedByCurrentThread();
    }


    private record ILockBiFunction() implements BiFunction<Set<LockWrapper>, LockWrapper, Lock>{
        @Override
        public Lock apply(Set<LockWrapper> held, LockWrapper lw) {
            if (held.add(lw)) lw.lock();
            return lw.iLock();
        }
    }

//    private record ReadLockBiFunction() implements BiFunction<Set<LockWrapper>, LockWrapper, Lock> {
//
//        @Override
//        public Lock apply(Set<LockWrapper> held, LockWrapper lw) {
//            if (held.add(lw)) lw.lock();
//            return lw.iLock();
//        }
//    }
}
