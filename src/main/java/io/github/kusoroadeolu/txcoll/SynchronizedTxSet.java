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
    private Transaction heldByParent; //The parent of the transaction

    public SynchronizedTxSet(){
        this.txSet = ConcurrentHashMap.newKeySet();
        this.lock = new ReentrantLock();
        this.lockHeld = new AtomicBoolean();
    }

    public boolean put(Transaction tx){
        return txSet.add(tx);
    }


    public LockStatus tryLock(Transaction tx){
        if (this.lockHeld.compareAndSet(false, true)){
            this.lock.lock();
            return LockStatus.HELD;
        }

        Option<Transaction> option = tx.parent();
        return switch (option){
            case Some<Transaction> s -> {
                if (s.unwrap().equals(heldByParent)){
                    yield LockStatus.HELD_WITHIN;
                }else yield LockStatus.NOT_HELD;
            }

            case None n -> LockStatus.NOT_HELD;
        };
    }

    public Lock unlock(){
        this.lock.unlock();
        return this.lock;
    }


    public enum LockStatus {
        HELD, HELD_WITHIN, //Held by a child transaction of the parent
        NOT_HELD //Not held at all
    }
}
