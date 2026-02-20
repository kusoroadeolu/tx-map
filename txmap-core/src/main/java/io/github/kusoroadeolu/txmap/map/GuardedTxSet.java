package io.github.kusoroadeolu.txmap.map;

import io.github.kusoroadeolu.txmap.Transaction;

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
class GuardedTxSet {
    private final Set<Transaction> txSet;
    private final Lock lock;  //This lock is mainly meant for write ops, read operations use optimistic validation instead
    private final AtomicInteger readerCount;
    private volatile Latch latch;

    //WRITER STATUS
    public static final int FREE = 0;
    public static final int HELD = 1;
    private final static Latch DEFAULT = new Latch(FREE, null ,null);



    public GuardedTxSet(){
        this.latch = DEFAULT;
        this.txSet = ConcurrentHashMap.newKeySet();
        this.lock = new ReentrantLock();
        this.readerCount = new AtomicInteger();
    }

    //Ensure only one tx can abort at a time, and only the tx that aborted can take the lock
    public void lockAndIncrement(Predicate<Set<GuardedTxSet>> shouldHold, Set<GuardedTxSet> held, Transaction tx){
        if (shouldHold.test(held)){
            this.lock.lock();
            latch = new Latch(HELD, tx.parent().unwrap() ,new CountDownLatch(1)); //Set both HELD and latch as a single atomic op. Prevents a scenario where a reader sees held but sees an old value of latch
            while (readerCount.get() > 0) Thread.onSpinWait(); //Wait for existing readers to commit
        }
    }

    public void lock(){
        this.lock.lock();
    }

    public void decrementThenRelease(){
        if (latch.equals(DEFAULT)) {
            this.lock.unlock();
            return;
        }

        var prev = latch.cLatch; //This cant be reordered cuz of synchronization guarantees
        latch = DEFAULT; //Ensure writers see we're free, before we countdown to prevent TOCTOU NPEs
        prev.countDown();
        this.lock.unlock();
    }

    public boolean put(Transaction tx){
        return txSet.add(tx);
    }

    public void remove(Transaction tx){
        txSet.remove(tx);
    }

    public Latch latch(){
        return this.latch;
    }

    public void incrementReaderCount(){
        readerCount.incrementAndGet();
    }

    public void decrementReaderCount(){
        readerCount.decrementAndGet();
    }



    record Latch(int status, Transaction parent ,CountDownLatch cLatch){
        public boolean isHeld(Transaction tx){
            return this.status == HELD && !this.parent.equals(tx.parent().unwrap());
        }
    }

}
