package io.github.kusoroadeolu.txcoll;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class SynchronizedTxSet {
    private final Set<Transaction> txSet;
    private final Lock rLock;
    private final Lock wLock;

    public SynchronizedTxSet(){
        this.txSet = ConcurrentHashMap.newKeySet();
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.rLock = rwLock.readLock();
        this.wLock = rwLock.writeLock();
    }

    //Ensure only one tx can abort at a time, and that tx that aborted can take the lock
    public Lock abortAll(){
        synchronized (this){
            txSet.forEach(Transaction::abort);
            return this.wLock;
        }

    }

    public boolean put(Transaction tx){
        return txSet.add(tx);
    }

    public Lock rLock(){
        return this.rLock;
    }

    public Lock wLock(){
        return this.wLock;
    }

}
