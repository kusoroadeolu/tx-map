package io.github.kusoroadeolu.txmap;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class FlatCombinedList<E> {

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


    private final ThreadLocal<StatefulAction<List<E>, Object>> local;
    private final static int SPIN_COUNT = 256;
    private final AtomicReference<Node<List<E>, ?>> head;
    private final List<E> list;
    private final ReentrantLock lock;
    private final static Node<List<?>, ?> DUMMY = new Node<>(null); //This marks the end of the "queue"
    private int count;
    private final int threshold;

    @SuppressWarnings("unchecked")
    public FlatCombinedList() {
        this.local = ThreadLocal.withInitial(StatefulAction::new);
        this.head = new AtomicReference<>((Node<List<E>, ?>) (Node<?, ?>) DUMMY);
        this.lock = new ReentrantLock();
        this.list = new ArrayList<>();
        this.threshold = 100;
    }


    @SuppressWarnings("unchecked")
    public <R>R run(Action<List<E>, R> action) {
        var stateful = local.get();
        setAction(action);

        if (stateful.isInactive()){
            Node<List<E>, Object> node = new Node<>(local);
            Node<List<E>, R> prevHead = (Node<List<E>, R>) head.getAndSet(node);
            node.setTail((Node<List<E>, Object>) prevHead);
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
    void setAction(Action<List<E>, ?> action) {
        var stateful = local.get();
        stateful.setAction((Action<List<E>, Object>) action);
    }

    @SuppressWarnings("unchecked")
    void scanCombineApply(){
        Node<List<E>, Object> seenHead = (Node<List<E>, Object>) this.head.get();
        Node<List<E>, Object> node = seenHead;

        while (!node.equals(DUMMY)){
            this.tryApply(node);
            while (node.tail == null) Thread.onSpinWait(); //This spin should be relatively short, we're using this to bridge the gap from when the node was set as the head to when we set its tailne
            node = node.tail;
        }

        if (count - threshold > 1){
            dequeFromHead(seenHead);
        }

    }

    private void dequeFromHead(Node<List<E>, Object> seenHead) {
        var head = seenHead;
        var current = seenHead.tail;
        StatefulAction<List<E>, ?> statefulAction;
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


    void tryApply(Node<List<E>, Object> node){
        var statefulAction =  node.local.get();
        if (statefulAction == null) return;

        if (statefulAction.canApply()) {
            statefulAction.apply(this.list);
            node.setCount(++count);
        }
    }

    public List<E> list(){
        return list;
    }
}
