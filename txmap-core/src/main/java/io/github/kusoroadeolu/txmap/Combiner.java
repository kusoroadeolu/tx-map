package io.github.kusoroadeolu.txmap;


import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class Combiner<E> {

    @FunctionalInterface
    public interface Action<E, R>{
        R apply(E e);
    }

    static class StatefulAction<E, R>{
       volatile Action<E, R> action;
       volatile R result;
       volatile boolean isApplied;

        private static final int ACTIVE = 1;
        private static final int INACTIVE = 0;
        private final AtomicInteger status;

        public StatefulAction() {
            this.status = new AtomicInteger(INACTIVE);
        }


       void setAction(Action<E, R> action){
           isApplied = false;
           this.action = action;
       }

        void apply(E e){
           if (canApply()) this.result = this.action.apply(e);
           this.isApplied = true;
        }

        boolean canApply(){
           return !this.isApplied;
        }

        R result(){
            return result;
        }


        void setInactive(){
            status.compareAndSet(ACTIVE, INACTIVE);
        }

        boolean isInactive(){
            return status.get() == INACTIVE;
        }

    }

    static class Node<E, R> {
        private final StatefulAction<E, R> statefulAction;
        private int count;

        private volatile Node<E, R> tail; //The node below this node

        Node(){
            this.statefulAction = new StatefulAction<>();
        }

        void setCount(int count){
            this.count = count;
        }

        void setTail(Node<E, R> node){
            tail = node;
        }

    }


    private final ThreadLocal<Node<E, ?>> local;
    private final static int SPIN_COUNT = 256;
    private final AtomicReference<Node<E, ?>> head;
    private final E e;
    private final ReentrantLock lock;
    private final static Node<List<?>, ?> DUMMY = new Node<>(); //This marks the end of the "queue"
    private int count;
    private final int threshold;

    @SuppressWarnings("unchecked")
    public Combiner(E e) {
        this.local = ThreadLocal.withInitial(Node::new);
        this.head = new AtomicReference<>((Node<E, Object>) DUMMY);
        this.lock = new ReentrantLock();
        this.e = e;
        this.threshold = 100;
    }


    @SuppressWarnings("unchecked")
    public <R>R combine(Action<E, R> action) {
        Node<E, R> node = (Node<E, R>) local.get();
        var stateful = node.statefulAction;
        setAction(action);

        if (stateful.isInactive()){
            Node<E, R> prevHead = (Node<E, R>) head.getAndSet(node);
            node.setTail(prevHead);
        }

        while (true){
            if (lock.tryLock()){
                try {
                    scanCombineApply();
                    if (stateful.isApplied) return stateful.result;
                }finally {
                    lock.unlock();
                }
            }

            int spins = 0;
            while (++spins < SPIN_COUNT) {
                Thread.onSpinWait();
            }

            if (stateful.isApplied) return stateful.result;
        }
    }


    @SuppressWarnings("unchecked")
    void setAction(Action<E, ?> action) {
        Node<E, Object> node = (Node<E, Object>) local.get();
        node.statefulAction.setAction((Action<E, Object>) action);
    }

    @SuppressWarnings("unchecked")
    void scanCombineApply(){
        Node<E, Object> seenHead = (Node<E, Object>) this.head.get();
        Node<E, Object> node = seenHead;

        while (!node.equals(DUMMY)){
            this.tryApply(node);
            while (node.tail == null) Thread.onSpinWait(); //This spin should be relatively short, we're using this to bridge the gap from when the node was set as the head to when we set its tail
            node = node.tail;
        }

        if (count - threshold > 1){
            dequeFromHead(seenHead);
        }

    }

    private void dequeFromHead(Node<E, Object> seenHead) {
        var head = seenHead;
        var current = seenHead.tail;
        StatefulAction<E, ?> statefulAction;
        while (!current.equals(DUMMY)){
            statefulAction = current.statefulAction;
            if ((count - threshold) >= current.count && statefulAction.isApplied){ //Ensure the user
                statefulAction.setInactive();
                head.setTail(current.tail);
                current.tail = null;
                break;
            }

            head = current;
            current = current.tail;
        }
    }


    void tryApply(Node<E, Object> node){
        var action =  node.statefulAction;
        if (action != null && action.canApply()) {
            action.apply(this.e);
            node.setCount(++count);
        }
    }

    public E e(){
        return e;
    }
}
