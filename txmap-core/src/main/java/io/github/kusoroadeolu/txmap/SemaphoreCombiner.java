package io.github.kusoroadeolu.txmap;


import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


//Rather than cleaning up old nodes, this combiner reuses nodes, it assumes a max of N threads using this combiner
/*
* So this combiner
*
* */
public class SemaphoreCombiner<E> implements Combiner<E>{


    static class StatefulAction<E, R>{
        Action<E, R> action;
        R result;
        boolean isApplied;

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
            this(NOT_COMBINER);
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

    /*
    * Node(Tail)
    * T1
    * Node(UnboundCombiner) -> Thread 1 Node (Not combiner)
    *
    * /T2
    * Thread 1 Node -> Thread 2 Node
    * Node(UnboundCombiner) -> Thread 1 Node (Not combiner) -> Thread 2 Node (Not combiner)
    *
    * UnboundCombiner ran, Node, Thread 1 Node and Thread 2 Node
    *
    * /T1
    * Thread 2 Node -> Node
    * */
    public SemaphoreCombiner(E e) {
        this(e, 100);
    }

    public SemaphoreCombiner(E e, int threshold) {
        this.local = ThreadLocal.withInitial(Node::new);
        this.head = new AtomicReference<>(new Node<>(Node.IS_COMBINER));
        this.e = e;
        this.threshold = threshold;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R>R combine(Action<E, R> action) {
        Node<E, R> newTail = (Node<E, R>) local.get();
        newTail.stateful.isApplied = false;
        newTail.status.lazySet(Node.NOT_COMBINER);


        var curNode = (Node<E, R>) head.getAndSet((Node<E, Object>) newTail);
        local.set(curNode);

        // A brief window where, we can't be reached by a former tail
        curNode.stateful.action = action;
        curNode.setNext(newTail);

        var stateful = curNode.stateful;

        while (curNode.status.get() == Node.NOT_COMBINER){
            int spins = 0;
            while (++spins < SPIN_COUNT) {
                Thread.onSpinWait();
            }
        }

        if (stateful.isApplied) return stateful.result;

        //Now we're the combiner
        Node<E, R> node = curNode;
        Node<E, R> next;

        for (int i = 0; i < threshold && (next = node.next) != null; ++i, node = next){
            node.stateful.apply(e);
            node.stateful.isApplied = true;

            node.stateful.action = null;
            node.next = null; //Break the link, volatile write immediately makes is applied and action visibile
            node.status.lazySet(Node.IS_COMBINER);
        }

        node.status.set(Node.IS_COMBINER);
        return stateful.result;
    }


    @Override
    public E e(){
        return e;
    }
}
