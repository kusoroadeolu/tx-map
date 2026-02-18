package io.github.kusoroadeolu.txcoll;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Function;

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

    //Ensure only one tx can abort at a time, and only the tx that aborted can take the lock
    public Lock abortAll(Set<Lock> locks){
        synchronized (this){
            txSet.forEach(Transaction::abort);
            return new LockBiFunction().apply(locks, wLock);
        }
    }

    public boolean put(Transaction tx){
        return txSet.add(tx);
    }

    public void remove(Transaction tx){
        txSet.remove(tx);
    }

    public Lock rLock(){
        synchronized (this){ //In the case a value already added to the map but not aborted trys to acquire a lock while writer is aborting
            return this.rLock;
        }
    }

    public Lock wLock(){
        return this.wLock;
    }


    private record LockBiFunction() implements BiFunction<Set<Lock>, Lock, Lock>{
        @Override
        public Lock apply(Set<Lock> locks, Lock lock) {
                if (locks.add(lock)) lock.lock();
                return lock;
            }
        }
}
