package io.github.kusoroadeolu.txmap;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AtomicArrayCombiner<E> implements Combiner<E> {

    static class StatefulAction<E, R>{
        Action<E, R> action; //The action should be volatile to ensure the combining thread sees it
        R result;
        volatile boolean isApplied; //Volatile fence for the result type, this should always be written after the result is set


        void apply(E e){
            this.result = this.action.apply(e);
            this.isApplied = true; //Volatile write here ensures result is eventually visible
        }


    }

    static class Node<E, R> {
        private final StatefulAction<E, R> stateful;

        public Node() {
            this.stateful = new StatefulAction<>();
        }

    }

    private final ThreadLocal<Node<E, ?>> local;
    private final AtomicReferenceArray<Node<E, ?>> cells;
    private final int capacity;
    private final E e;
    private final Lock lock;
    private final AtomicLong cellNum; //The cell in the array the thread's node will be assigned to
    private int pointer; //The pointer to which the last combiner stopped at, i.e. if a combiner stopped at cell 2, the next combiner should start from cell 3

    public AtomicArrayCombiner(E e, int capacity) {
        this.local = ThreadLocal.withInitial(Node::new);
        this.capacity = capacity;
        this.e = e;
        this.lock = new ReentrantLock();
        this.cells = new AtomicReferenceArray<>(this.capacity);
        this.cellNum = new AtomicLong(0);
    }

    public AtomicArrayCombiner(E e) {
       this(e, 100);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R combine(Action<E, R> action) {
        Node<E, R> node = (Node<E, R>) local.get();
        node.stateful.action = action;
        node.stateful.isApplied = false; //Volatile write ensures the visibility of action before it is put into the arr

        int cell = (int) (cellNum.getAndIncrement() % capacity); //Since our cells start at 0, we get then increment, to prevent the case in which we always leave the first cell empty

        //When that node is null, we try to cas, if we can't cas from null to our current cell, that means another thread has acquired the cell
        while (!cells.compareAndSet(cell, null, node)){
            Thread.yield();
        }

        //Once we've acquired the lock, each thread should spin on it's node, until their value has been acquired
        int maxSpins = 256;
        while (!node.stateful.isApplied){
            if (lock.tryLock()){ //Try the lock, if applied, we're the combiner
                try {
                    this.scanCombineApply();
                    return node.stateful.result;
                }finally {
                    lock.unlock();
                }
            }

            int spins = 0;
            while (++spins < maxSpins) Thread.onSpinWait();
        }

        return node.stateful.result;
    }


    void scanCombineApply(){
        int ptr = this.pointer;
        int count = 0;
        Node<E, ?> curr;

        while ((curr = cells.get(ptr)) != null && count < capacity){
            curr.stateful.apply(e); //Volatile write here, the ordering of set opaque doesnt matter since we aren't writing to shared fields after we set opaque to null
            cells.set(ptr, null); //Then mark that we've applied the node, we actually don't need a fence here, all we need is a visibility guarantee that ensures waiting threads see that this is immediately null
            if (++ptr == capacity) ptr = 0; //If we've reached the threshold, reset to 0
            ++count;
        }

        this.pointer = ptr; //Always set the pointer back to ptr
    }

    @Override
    public E e() {
        return e;
    }
}
