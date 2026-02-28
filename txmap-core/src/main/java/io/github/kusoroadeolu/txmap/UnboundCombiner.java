package io.github.kusoroadeolu.txmap;


import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static io.github.kusoroadeolu.txmap.UnboundCombiner.StatefulAction.ACTIVE;

public class UnboundCombiner<E> implements Combiner<E>{

    static class StatefulAction<E, R>{
        volatile Action<E, R> action;
        R result;

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
            status.setOpaque(INACTIVE);
        }

        boolean isInactive(){
            return status.get() == INACTIVE;
        }

        void setActive(){
            this.status.setOpaque(ACTIVE);
        } //Set plain should actually work fine here, since set active is always piggybacked by an atomic swap to the head of the linked queue which guarantees eventual visibility

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
        var stateful = node.statefulAction; //Stateful action reference is always visible due to final field visibility guarantees
        stateful.setActionAndNullResult(action);  //Volatile write to action ensures result is always null before we enqueue

        if (stateful.isInactive()){
            node.statefulAction.setActive(); //This is always visible and cant be reordered because of volatile write to head.getAndSet, setTail
            Node<E, R> prevHead = (Node<E, R>) head.getAndSet(node); //Ensure we enqueue as the head before we set the tail
            node.setTail(prevHead); //Set the tail to node, there's a brief window where our tail is unreachable hence the null check
        }

        while (stateful.action != null){
            if (lock.tryLock()){
                try {
                    scanCombineApply();
                    if(stateful.action == null) return stateful.result; //Return while we still hold the lock, ensure we check before we return in the case a combiner removed our node before we acquired the lock and became the combiner
                }finally {
                    lock.unlock();
                }
            }

            int spins = 0;
            while (++spins < SPIN_COUNT) {
                Thread.onSpinWait();
            }

            //Ensure to always check in the loop, just to prevent a situation where we think we're still active but we're not
            if (stateful.isInactive()){
                node.statefulAction.setActive(); //This is always visible and cant be reordered because of volatile write to head.getAndSet, setTail
                Node<E, R> prevHead = (Node<E, R>) head.getAndSet(node); //Ensure we enqueue as the head before we set the tail
                node.setTail(prevHead);
            }
        }

        return stateful.result;
    }

    @SuppressWarnings("unchecked")
    void scanCombineApply(){
        Node<E, Object> seenHead = (Node<E, Object>) this.head.get(); //Volatile read, also head should never be null, it should either be the dummy or some other node
        Node<E, Object> node = seenHead;
        ++count;

        while (node != null && !node.equals(DUMMY)){ //Ensure node != null before we continue
            this.apply(node);
            node = node.tail;
        }

        //We should never remove the dummy node, else NPE issues could occur
        if (seenHead != null && count >= threshold){
            dequeFromHead(seenHead); //Seen head can never be null
        }

    }

    private void dequeFromHead(Node<E, Object> seenHead) {
        var head = seenHead;
        var current = seenHead.tail;
        StatefulAction<E, ?> statefulAction;
        while (current != null && !current.equals(DUMMY)){
            statefulAction = current.statefulAction;
            if ((count - current.count) >= threshold){
                head.setTail(current.tail);
                current.tail = null;
                current = head.tail;
                statefulAction.setInactive(); //Ensure we set this as inactive after unlinking to prevent a situation in which this thread and the node owning threader overwrite their tail
                //i.e the node owning thread, sees its inactive and, could try to set its tail to the current head , but the combiner overwrites it, now the node is unlinked forever while still being active, leading to issues
                //Set opaque is more enticing here because we dont need a fully volatile write, since the writes to the tails are volatile, preventing reordering
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