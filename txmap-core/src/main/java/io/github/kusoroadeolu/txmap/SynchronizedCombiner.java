package io.github.kusoroadeolu.txmap;

//Not really a combiner, just here to test if combiners actually amortize synchronization overhead
public class SynchronizedCombiner<E> implements Combiner<E>{
    private final Object lock;
    private final E e;

    public SynchronizedCombiner(E e) {
        this.lock = new Object();
        this.e = e;
    }

    @Override
    public <R> R combine(Action<E, R> action) {
        return this.combine(action, null);
    }

    @Override
    public <R> R combine(Action<E, R> action, IdleStrategy strategy) {
        synchronized (lock){
            return action.apply(e);
        }
    }

    @Override
    public E e() {
        return e;
    }
}
