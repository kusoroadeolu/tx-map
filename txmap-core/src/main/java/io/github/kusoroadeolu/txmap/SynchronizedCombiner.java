package io.github.kusoroadeolu.txmap;

import java.util.concurrent.locks.ReentrantLock;

//Not really a combiner, just here to test if combiners actually amortize synchronization overhead
public class SynchronizedCombiner<E> implements Combiner<E>{
    private final ReentrantLock lock;
    private Object result;
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
    @SuppressWarnings("unchecked")
    public <R> R combine(Action<E, R> action, IdleStrategy strategy) {
        this.lock.lock();
        try {
            result = action.apply(e);
            return (R) result;
        }finally {
            this.lock.unlock();
        }
    }

    @Override
    public E e() {
        return e;
    }
}
