package io.github.kusoroadeolu.txmap;

import java.util.concurrent.atomic.AtomicInteger;
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
            this.isApplied = true; //Volatile write ensures value of result is eventually visible, we might not need a full volatile write here, a set release(lazy set) write will be enough here
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
    private final AtomicInteger cellNum; //The cell in the array the thread's node will be assigned to
    private int pointer; //The pointer to which the last combiner stopped at, i.e. if a combiner stopped at cell 2, the next combiner should start from cell 3

    public AtomicArrayCombiner(E e, int capacity) {
        this.local = ThreadLocal.withInitial(Node::new);
        this.capacity = capacity;
        this.e = e;
        this.lock = new ReentrantLock();
        this.cells = new AtomicReferenceArray<>(this.capacity);
        this.cellNum = new AtomicInteger(0);
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

        int cell = cellNum.getAndIncrement() % capacity; //Since our cells start at 0, we get then increment, to prevent the case in which we always leave the first cell empty

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
                    if (node.stateful.isApplied) return node.stateful.result; //Volatile read ensures value of "result" is eventually visible
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
        /*
         * Lets's quickly trace through my ptr logic starting from zero
         * we have 2 nodes, and a late arriving node, we process those two nodes before the late arrival, our value should be 2 and not 1 or 3
         * 0 -> apply then incr
         * 1 -> apply then incr, after this we don't see the late arriving node, so we unbecome the combiner
         * So our current value is 2, which is correct! Nice!
         * */
        while ((curr = cells.get(ptr)) != null && count < capacity){
            curr.stateful.apply(e); //Volatile write from is applied
            cells.setOpaque(ptr, null); //Then mark that we've applied the node
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
