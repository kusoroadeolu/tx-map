package io.github.kusoroadeolu.txmap;


import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


//Rather than cleaning up old nodes, this combiner reuses nodes, it assumes a max of N threads using this combiner
/* Happens before guarantees
* The reset of a current thread local node happens before the swap of the current shared node
* Setting the action the current thread wants to perform happens before we link our current thread local node to our old thread local node
* The unlinking application of each node's action by the combiner happens before the node is unlinked
* The setting of each node as the combiner happens before
*
* */
public class SemaphoreCombiner<E> implements Combiner<E>{


    static class StatefulAction<E, R>{
        Action<E, R> action;
        R result;
        boolean isApplied;

        void apply(E e){
            if (canApply()) {
                this.result = this.action.apply(e);
                this.isApplied = true;
            }

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

    /*
    * 1x
    * 1x, 0y Thread 1, and 0y is the current node
    * 1x, 0y, 0z where 1 is the combiner and x is the thread name Thread 2 and 0z is the current node
    * Then we combine
    * 1x -> 1, 0y -> 1y, 0z -> 1z, and 1z is now the combiner and unlink them all
    * Thread 1 comes again, holding 1x
    * 1z, 0x(combiner in atomic ref)
    * Thread 2 comes holding 0y
    * 1z, 0x, 0y, then we combine again
    * Therefore only one combiner can be active at a time
    * */

    /*
     * 1x
     * 1x, 0y Thread 1, and 0y is the current node
     * 1x, 0y, 0z where 1 is the combiner and x is the thread name Thread 2 and 0z is the current node
     * Then we combine
     * 1x -> 0x, 0y -> 0y, 0z -> 1z, and 1z is now the combiner since it was the tail and unlink them all as we combine
     * Thread 1 comes again, holding 1x
     * 1z, 0x(combiner in atomic ref)
     * Thread 2 comes holding 0y
     * 1z, 0x, 0y, then we combine again
     * Therefore only one combiner can be active at a time
     * */
    @SuppressWarnings("unchecked")
    @Override
    public <R>R combine(Action<E, R> action) {
        //Get our thread local node, reset it and make it not the combiner
        var newTail = (Node<E, R>) local.get();
        newTail.stateful.isApplied = false;
        newTail.status.lazySet(Node.NOT_COMBINER);

        //Then set our thread local node to be the new tail, retrieve the old tail, and set it as our thread local node
        var curNode = (Node<E, R>) head.getAndSet((Node<E, Object>) newTail);
        local.set(curNode);

        //Set our action for our current thread local node, and set our old thread local node as our tail,
        curNode.stateful.action = action;
        curNode.setNext(newTail);

        var stateful = curNode.stateful;

        //While we aren't the combiner, for instance a combiner has already claimed the combining node, wait, otherwise, we're the combiner
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

        //Then apply each node and set them as the combiner, at this point, there will no node that's the combiner
        for (int i = 0; i < threshold && (next = node.next) != null; ++i, node = next){
            node.stateful.apply(e);
            node.stateful.isApplied = true;

            node.stateful.action = null;
            node.next = null; //Break the link, volatile write immediately makes is applied and action visible
            //After we set each node as the combiner, remember that at the beginning, each node always resets their combining status, so multiple nodes cannot be the combiner, so when a thread gets the current atomic node, our
            node.status.lazySet(Node.IS_COMBINER);
        }

        node.status.lazySet(Node.IS_COMBINER); //Set the current tail up to x threshold (basically the note in atomic ref) to be the combiner, this ensures all "IS COMBINER writes are also visible"
        //Looking back at this we don't need a full volatile set here, a set-release fence preventing the reordering of writes in the loop after the last node status is set, is better and less expensive
        return stateful.result;
    }


    @Override
    public E e(){
        return e;
    }
}
