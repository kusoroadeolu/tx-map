package io.github.kusoroadeolu.txmap.map;

import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.txmap.Transaction;
import io.github.kusoroadeolu.txmap.map.OptimisticTransactionalMap.LockWrapper;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;

//Happens before edges
/*
* A abort of transactions in the set by a transaction happens before the acquisition of the write lock by the transaction that aborted
* */
class GuardedTxSet {
    private final Set<Transaction> txSet;
    private final Lock rLock;
    private final Lock wLock;
    private static final ILockBiFunction BI_FUNCTION = new ILockBiFunction();

    public GuardedTxSet(){
        this.txSet = ConcurrentHashMap.newKeySet();
        var rwLock = new ReentrantReadWriteLock();
        this.rLock = rwLock.readLock();
        this.wLock = rwLock.writeLock();
    }



    public boolean put(Transaction tx){
        return txSet.add(tx);
    }

    public void remove(Transaction tx){
        txSet.remove(tx);
    }

    public void uniqueAcquireReadLock(Set<LockWrapper> heldLocks, Operation op){
        Option.ofNullable(heldLocks)
                    .map(slw ->
                            BI_FUNCTION.apply(slw, new LockWrapper(OptimisticTransactionalMap.LockType.READ, op, rLock))
                    );
    }


    //This is only held by write transactions, for read transactions that
    public Lock writeLock(){
        return this.wLock;
    }

    private record ILockBiFunction() implements BiFunction<Set<LockWrapper>, LockWrapper, Lock>{
        @Override
        public Lock apply(Set<LockWrapper> held, LockWrapper lw) {
            if (held.add(lw)) lw.lock();
            return lw.iLock();
        }
    }
}
