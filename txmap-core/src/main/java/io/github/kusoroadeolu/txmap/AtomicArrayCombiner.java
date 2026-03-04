package io.github.kusoroadeolu.txmap;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static io.github.kusoroadeolu.txmap.Combiner.IdleStrategy.busySpin;

public class AtomicArrayCombiner<E> implements Combiner<E> {

    public static class StatefulAction<E, R>{
        Action<E, R> action; //The action should be volatile to ensure the combining thread sees it
        public R result;
        public volatile boolean isApplied; //Volatile fence for the result type, this should always be written after the result is set


        public StatefulAction(Action<E, R> action){
            this.action = action;
        }

        StatefulAction(){
            this(null);
        }

        public void apply(E e){
            this.result = this.action.apply(e);
            this.isApplied = true; //Volatile write ensures value of result is eventually visible, we might not need a full volatile write here, a set release(lazy set) write will be enough here
        }



    }

    public static class Node<E, R> {
        public final StatefulAction<E, R> stateful;

        public Node() {
            this.stateful = new StatefulAction<>();
        }

        public Node(Action<E, R> action) {
            this.stateful = new StatefulAction<>(action);
        }

    }

    private final ThreadLocal<Node<E, ?>> local;
    private final AtomicReferenceArray<Node<E, ?>> cells;
    private final int capacity;
    private final E e;
    private final Lock lock;
    private final AtomicLong cellNum; //The cell in the array the thread's node will be assigned to

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
    public <R> R combine(Action<E, R> action) {
        return combine(action, busySpin());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R combine(Action<E, R> action, IdleStrategy strategy) {
        Node<E, R> node = (Node<E, R>) local.get();
        node.stateful.action = action;
        node.stateful.isApplied = false; //Volatile write ensures the visibility of action before it is put into the arr

        int cell = (int) (cellNum.getAndIncrement() % capacity); //Since our cells start at 0, we get then increment, to prevent the case in which we always leave the first cell empty

        //When that node is null, we try to cas, if we can't cas from null to our current cell, that means another thread has acquired the cell
        while (!cells.compareAndSet(cell, null, node)){
            Thread.yield();
        }

        //Once we've acquired the lock, each thread should spin on its node, until their value has been acquired
        int idleCount = 0;
        while (!node.stateful.isApplied){
            if (lock.tryLock()){ //Try the lock, if applied, we're the combiner
                try {
                    this.scanCombineApply();
                    return node.stateful.result;
                }finally {
                    lock.unlock();
                }
            }

            idleCount = strategy.idle(idleCount);
        }

        return node.stateful.result;
    }

    void scanCombineApply(){
        for (int i = 0; i < capacity; i++){
            Node<E, ?> curr = cells.get(i);
            if (curr != null){
                curr.stateful.apply(e);
                cells.setOpaque(i, null);
            }
        }
    }


    @Override
    public E e() {
        return e;
    }
}
