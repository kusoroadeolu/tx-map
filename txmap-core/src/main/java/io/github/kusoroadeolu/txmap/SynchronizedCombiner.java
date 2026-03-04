package io.github.kusoroadeolu.txmap;

import java.util.concurrent.locks.ReentrantLock;

//Not really a combiner, just here to test if combiners actually amortize synchronization overhead
public class SynchronizedCombiner<E> implements Combiner<E>{
    private final ReentrantLock lock;
    private final E e;

    public SynchronizedCombiner(E e) {
        this.lock = new ReentrantLock();
        this.e = e;
    }

    @Override
    public <R> R combine(Action<E, R> action) {
        return this.combine(action, null);
    }

    @Override
    public <R> R combine(Action<E, R> action, IdleStrategy strategy) {
        this.lock.lock();
        try {
            return action.apply(e); //Ideally this shouldn't get optimized out by the JVM due it operating on a shared value
        }finally {
            this.lock.unlock();
        }
    }

    @Override
    public E e() {
        return e;
    }
}
