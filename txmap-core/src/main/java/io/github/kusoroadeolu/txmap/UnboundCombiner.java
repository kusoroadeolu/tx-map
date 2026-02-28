package io.github.kusoroadeolu.txmap;


import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static io.github.kusoroadeolu.txmap.UnboundCombiner.StatefulAction.ACTIVE;

public class UnboundCombiner<E> implements Combiner<E>{

    static class StatefulAction<E, R>{
        volatile Action<E, R> action;
        volatile R result;

        static final int ACTIVE = 1;
        static final int INACTIVE = 0;
        private final AtomicInteger status;

        public StatefulAction() {
            this.status = new AtomicInteger(INACTIVE);
        }


        void setActionAndNullResult(Action<E, R> action){
            this.result = null;
            this.action = action;
        }

        void applyAndNullAction(E e){
            this.result = this.action.apply(e);
            this.action = null; //Null out the action to notify the caller
        }

        boolean canApply(){
            return action != null;
        }

        R result(){
            return result;
        }


        void setInactive(){
            status.set(INACTIVE);
        }

        boolean isInactive(){
            return status.get() == INACTIVE;
        }

        void setActive(){
            this.status.set(ACTIVE);
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
    private final static Node<?, ?> DUMMY = new Node<>(); //This marks the end of the "queue"
    private int count;
    private final int threshold;

    @SuppressWarnings("unchecked")
    public UnboundCombiner(E e) {
        this(e, 100);
    }

    @SuppressWarnings("unchecked")
    public UnboundCombiner(E e, int threshold) {
        this.local = ThreadLocal.withInitial(Node::new);
        this.head = new AtomicReference<>((Node<E, Object>) DUMMY);
        this.lock = new ReentrantLock();
        this.e = e;
        this.threshold = threshold;
    }

    @SuppressWarnings("unchecked")
    public <R>R combine(Action<E, R> action) {
        Node<E, R> node = (Node<E, R>) local.get();
        var stateful = node.statefulAction;
        stateful.setActionAndNullResult(action);

        if (stateful.isInactive()){
            node.statefulAction.setActive();
            Node<E, R> prevHead = (Node<E, R>) head.getAndSet(node);
            node.setTail(prevHead);
        }

        int spins;
        while (stateful.action != null){
            spins = 0;
            if (lock.tryLock()){
                try {
                    scanCombineApply();
                    if (stateful.action == null) return stateful.result;

                }finally {
                    lock.unlock();
                }
            }


            while (++spins < SPIN_COUNT) {
                Thread.onSpinWait();
            }

            //Ensure to always check in the loop, just to prevent a situation where we think we're still active but we're not
            if (stateful.isInactive()){
                node.statefulAction.setActive();
                Node<E, R> prevHead = (Node<E, R>) head.getAndSet(node);
                node.setTail(prevHead);
            }
        }

        return stateful.result;
    }

    @SuppressWarnings("unchecked")
    void scanCombineApply(){
        Node<E, Object> seenHead = (Node<E, Object>) this.head.get();
        Node<E, Object> node = seenHead;
        ++count;

        while (!node.equals(DUMMY)){
            this.apply(node);
            node = node.tail;
        }


        if (count >= threshold){
            dequeFromHead(seenHead);
        }

    }

    private void dequeFromHead(Node<E, Object> seenHead) {
        var head = seenHead;
        var current = seenHead.tail;
        StatefulAction<E, ?> statefulAction;
        while (current != null && !current.equals(DUMMY)){
            statefulAction = current.statefulAction;
            if ((count - current.count) >= threshold && statefulAction.action == null){
                head.setTail(current.tail);
                current.tail = null;
                current = head.tail;
                statefulAction.setInactive(); //Ensure we set this as inactive after unlinking to prevent a situation in which this thread and the node owning threader overwrite their tail
                //I.e the node owning thread, sees its inactive and, could try to set its tail to the current head , but the combiner overwrites it, now the node is unlinked forever while still being active, leading to issues
                continue;
            }

            head = current;
            current = current.tail;
        }
    }


    void apply(Node<E, Object> node){
        var statefulAction =  node.statefulAction;
        if (statefulAction.canApply()) {
            statefulAction.applyAndNullAction(e);
            node.setCount(count);
        }
    }

    public E e(){
        return e;
    }

}