package io.github.kusoroadeolu.txcoll.map;

import io.github.kusoroadeolu.txcoll.Transaction;
import io.github.kusoroadeolu.txcoll.map.TransactionalMap.ChildMapTransaction;
import io.github.kusoroadeolu.txcoll.map.TransactionalMap.LockWrapper;
import io.github.kusoroadeolu.txcoll.map.TransactionalMap.MapTransactionImpl;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;

class SynchronizedTxSet {
    private final Set<Transaction> txSet;
    private final Lock rLock;
    private final Lock wLock;

    public SynchronizedTxSet(){
        this.txSet = new HashSet<>();
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.rLock = rwLock.readLock();
        this.wLock = rwLock.writeLock();
    }

    //Ensure only one tx can abort at a time, and only the tx that aborted can take the lck
    public Lock abortAll(ChildMapTransaction<?, ?> aborter){
        synchronized (this){
            txSet.stream().filter(tx -> !tx.parent().equals(aborter.parent())).forEach(Transaction::abort);
            var lws = aborter.parent().map(p -> ((MapTransactionImpl<?, ?>) p).heldLocks).unwrap();
            return new LockBiFunction().apply(lws , new LockWrapper(TransactionalMap.LockType.WRITE, aborter.operation, null));
        }
    }

    public boolean put(Transaction tx){
        return txSet.add(tx);
    }

    public void remove(Transaction tx){
        txSet.remove(tx);
    }

    public Lock rLock(){
        synchronized (this){ //In the case a value already added to the map but not aborted trys to acquire a lck while writer is aborting
            return this.rLock;
        }
    }

    public Lock wLock(){
        return this.wLock;
    }


    private record LockBiFunction() implements BiFunction<Set<LockWrapper>, LockWrapper, Lock>{
        @Override
        public Lock apply(Set<LockWrapper> lws, LockWrapper lw) {
            if (lws.add(lw)) lw.lock();
            return lw.lck();
        }
    }
}
