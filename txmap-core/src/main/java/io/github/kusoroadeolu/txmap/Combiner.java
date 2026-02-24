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
       Action<E, R> action;
       volatile R result;
       boolean isApplied;

        private static final int ACTIVE = 1;
        private static final int INACTIVE = 0;
        private final AtomicInteger status;

        public StatefulAction() {
            this.status = new AtomicInteger(INACTIVE);
        }


       void setAction(Action<E, R> action){
           this.action = action;
           isApplied = false;
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

        private final ThreadLocal<StatefulAction<E, R>> local;
        private int count;

        private volatile Node<E, R> tail; //The node below this node

        Node(ThreadLocal<StatefulAction<E, R>> local){
            this.local = local;
        }

        void setCount(int count){
            this.count = count;
        }

        void setTail(Node<E, R> node){
            tail = node;
        }

    }


    private final ThreadLocal<StatefulAction<E, Object>> local;
    private final static int SPIN_COUNT = 256;
    private final AtomicReference<Node<E, ?>> head;
    private final E e;
    private final ReentrantLock lock;
    private final static Node<List<?>, ?> DUMMY = new Node<>(null); //This marks the end of the "queue"
    private int count;
    private final int threshold;

    @SuppressWarnings("unchecked")
    public Combiner(E e) {
        this.local = ThreadLocal.withInitial(StatefulAction::new);
        this.head = new AtomicReference<>((Node<E, ?>) DUMMY);
        this.lock = new ReentrantLock();
        this.e = e;
        this.threshold = 100;
    }


    @SuppressWarnings("unchecked")
    public <R>R run(Action<E, R> action) {
        var stateful = local.get();
        setAction(action);

        if (stateful.isInactive()){
            Node<E, Object> node = new Node<>(local);
            Node<E, R> prevHead = (Node<E, R>) head.getAndSet(node);
            node.setTail((Node<E, Object>) prevHead);
        }

        while (true){
            if (lock.tryLock()){
                try {
                    scanCombineApply();
                    if (stateful.isApplied) return (R) stateful.result;
                }finally {
                    lock.unlock();
                }
            }

            int spins = 0;
            while (++spins < SPIN_COUNT) {
                if (stateful.isApplied) return (R) stateful.result;
                Thread.yield();
            }
        }
    }


    @SuppressWarnings("unchecked")
    void setAction(Action<E, ?> action) {
        var stateful = local.get();
        stateful.setAction((Action<E, Object>) action);
    }

    @SuppressWarnings("unchecked")
    void scanCombineApply(){
        Node<E, Object> seenHead = (Node<E, Object>) this.head.get();
        Node<E, Object> node = seenHead;

        while (!node.equals(DUMMY)){
            this.tryApply(node);
            while (node.tail == null) Thread.onSpinWait(); //This spin should be relatively short, we're using this to bridge the gap from when the node was set as the head to when we set its tailne
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
            statefulAction = current.local.get();
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
        var statefulAction =  node.local.get();
        if (statefulAction == null) return;

        if (statefulAction.canApply()) {
            statefulAction.apply(this.e);
            node.setCount(++count);
        }
    }

    public E e(){
        return e;
    }
}
