package io.github.kusoroadeolu.txcoll;

import io.github.kusoroadeolu.ferrous.option.None;
import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.ferrous.option.Some;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class SynchronizedTxSet {
    private final Set<Transaction> txSet;
    private final Lock lock;
    private final AtomicBoolean lockHeld;
    private volatile Transaction heldBy;

    public SynchronizedTxSet(){
        this.txSet = ConcurrentHashMap.newKeySet();
        this.lock = new ReentrantLock();
        this.lockHeld = new AtomicBoolean();
    }

    public boolean put(Transaction tx){
        return txSet.add(tx);
    }


    public LockWrapper tryLock(Transaction tx){
        if (this.lockHeld.compareAndSet(false, true)){
            this.lock.lock();
            this.heldBy = tx; // A race condition can occur here, could be fatal, we should probably use a stronger synchronization primitive here
            return new LockWrapper(Option.some(this.lock), LockStatus.HELD);
        }

        Option<Transaction> option = tx.parent();
        return switch (option){
            case Some<Transaction> s -> {
                if (s.unwrap().equals(this.heldBy.parent().unwrap())){
                    yield new LockWrapper(Option.none(), LockStatus.HELD_WITHIN);
                }else yield new LockWrapper(Option.none(), LockStatus.NOT_HELD);
            }

            case None n ->  new LockWrapper(Option.none(), LockStatus.NOT_HELD);
        };
    }

    public Lock unlock(){
        this.lock.unlock();
        return this.lock;
    }


    record LockWrapper(Option<Lock> lock, LockStatus status){}


    public enum LockStatus {
        HELD, HELD_WITHIN, //Held by a child transaction of the parent
        NOT_HELD //Not held at all
    }
}
