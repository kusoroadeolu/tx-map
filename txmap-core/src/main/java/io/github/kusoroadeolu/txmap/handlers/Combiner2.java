package io.github.kusoroadeolu.txmap.handlers;


import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


//Rather than cleaning up old nodes, this combiner reuses nodes, it assumes a max of N threads using this combiner
/*
* So this combiner
*
* */
public class Combiner2<E> {
    @FunctionalInterface
    public interface Action<E, R>{
        R apply(E e);
    }

    static class StatefulAction<E, R>{
        Action<E, R> action;
        volatile R result;
        volatile boolean isApplied;

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

    }

    static class Node<E, R> {
        private volatile Node<E, R> next; //The node below this node
        private final StatefulAction<E, R> stateful;
        private final AtomicInteger status;
        private static final int NOT_COMBINER = 0; //
        private static final int IS_COMBINER = 1;


        public Node() {
            this.stateful = new StatefulAction<>();
            this.status = new AtomicInteger(NOT_COMBINER);
        }

        public Node(int isCombiner) {
            this.stateful = new StatefulAction<>();
            this.status = new AtomicInteger(isCombiner);
        }

        void setNext(Node<E, R> node){
            next = node;
        }

    }

    private final ThreadLocal<Node<E, ?>> local;


    private final static int SPIN_COUNT = 256;
    private final AtomicReference<Node<E, Object>> head;
    private final E e;
    private final int threshold;

    public Combiner2(E e) {
        this.local = ThreadLocal.withInitial(Node::new);
        this.head = new AtomicReference<>(new Node<>(Node.IS_COMBINER));
        this.e = e;
        this.threshold = 100;
    }


    @SuppressWarnings("unchecked")
    public <R>R run(Action<E, R> action) {
        Node<E, R> newTail = (Node<E, R>) local.get();
        newTail.stateful.isApplied = false;
        newTail.status.set(0);  //Is applied will become visible immediately after newHead.status because of it's a volatile set


        Node<E, R> curNode = (Node<E, R>) head.getAndSet((Node<E, Object>) newTail);
        local.set(curNode);

        // A brief window where, newHead can't reach us
        curNode.stateful.setAction(action);
        curNode.setNext(newTail);

        var stateful = curNode.stateful;

        while (curNode.status.get() == Node.NOT_COMBINER){
            int spins = 0;
            while (++spins < SPIN_COUNT) {
                Thread.yield();
            }
        }

        if (stateful.isApplied) return stateful.result;


        Node<E, R> node = curNode;
        Node<E, R> next;

        for (int i = 0; i < threshold && (next = node.next) != null; ++i, node = next){
            node.stateful.apply(e);
            node.stateful.setAction(null);
            node.next = null;
            node.status.set(Node.IS_COMBINER);
        }

        node.status.lazySet(1);

        return stateful.result;
    }



    public E e(){
        return e;
    }
}
