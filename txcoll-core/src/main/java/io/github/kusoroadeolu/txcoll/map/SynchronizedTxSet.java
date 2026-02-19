package io.github.kusoroadeolu.txcoll.map;

import io.github.kusoroadeolu.txcoll.Transaction;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

// So on this branch, rather than aborts, we're simply going to use optimistic integers
/*  Ideally count can only be zero or one
//Happens before edges
* The acquisition of the lock of a set happens before the status is set to HELD. This guarantee is upheld by synchronization happens before guarantee
* The release of the lock of a set happens before the status is set to FREE. This guarantee is upheld by synchronization happens before guarantee
* The draining of readers happens before a writer can proceed. This guarantee is upheld by volatile read semantics of atomic reads
* */
class SynchronizedTxSet {
    private final Set<Transaction> txSet;
    private final Lock lock;  //This lock is mainly meant for write ops, read operations use optimistic validation instead
    private final AtomicInteger readerCount;
    private volatile Latch holder;
    public static final int FREE = 0;
    public static final int HELD = 1;
    private final static Latch DEFAULT = new Latch(FREE, null);

    public SynchronizedTxSet(){
        this.txSet = ConcurrentHashMap.newKeySet();
        this.lock = new ReentrantLock();
        this.readerCount = new AtomicInteger();
        this.holder = DEFAULT;
    }

    //Ensure only one tx can abort at a time, and only the tx that aborted can take the lock
    public void lockAndIncrement(Predicate<Set<Lock>> shouldHold, Set<Lock> held){
        if (shouldHold.test(held)){
            this.lock.lock();
            holder = new Latch(HELD, new CountDownLatch(1)); //Set both HELD and latch as a single atomic op. Prevents a scenario where a reader sees held but sees an old value of latch
            //Signal readers to stop entering
            while (readerCount.get() != 0) Thread.onSpinWait(); //Wait for existing readers to commit

        }
    }


    public Lock getLock(){
        return lock;
    }

    public Lock unlock(){
        this.lock.unlock();
        return lock;
    }

    public Lock decrementThenRelease(){
        var prevLatch = holder.writerLatch();
        holder = DEFAULT;
        prevLatch.countDown();
        return this.unlock();
    }

    public boolean put(Transaction tx){
        return txSet.add(tx);
    }

    public void remove(Transaction tx){
        txSet.remove(tx);
    }

    public boolean isHeld(){
        return this.holder.status == HELD;
    }

    public CountDownLatch latch(){
        return holder.writerLatch;
    }

    public void incrementReaderCount(){
        readerCount.incrementAndGet();
    }

    public void decrementReaderCount(){
        readerCount.decrementAndGet();
    }



    record Latch(int status, CountDownLatch writerLatch){}

}
