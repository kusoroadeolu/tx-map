package io.github.kusoroadeolu.txmap;


import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

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
            this.result = null; //Visible due to volatile fence
            this.action = action;
        }

        void applyAndNullAction(E e){
            this.result = this.action.apply(e);
            this.action = null; //Null out the action to notify the caller
        }

        boolean canApply(){
            return action != null;
        }


        void setInactive(){
            status.set(INACTIVE);
        }

        boolean isInactive(){
            return status.get() == INACTIVE;
        }


        //Will always be written before a volatile write
        void setActive(){
            this.status.setPlain(ACTIVE);
        }

    }

    static class Node<E, R> {
        private final StatefulAction<E, R> statefulAction;
        private int age;
        private volatile Node<E, R> next; //The node below this node

        Node(){
            this.statefulAction = new StatefulAction<>();
        }

        void setAge(int count){
            this.age = count;
        }

        void setNext(Node<E, R> node){
            next = node;
        }

    }


    private final ThreadLocal<Node<E, ?>> local;
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
        this.head = new AtomicReference<>((Node<E, ?>) DUMMY);
        this.lock = new ReentrantLock();
        this.e = e;
        this.threshold = threshold;
    }


    public <R>R combine(Action<E, R> action) {
        return combine(action, IdleStrategy.busySpin());
    }


    @Override
    @SuppressWarnings("unchecked")
    /*
    * Initialize node from thread local
    * Assign action and set result to null
    * If our node is inactive, enqueue using a single cas to the head of the queue
    *   Repeatedly
    *      check if our action is null
    *      try to acquire the lock
    *      if we fail
    *           idle
    *           after idling, check if we're inactive, if so enqueue using a single cas to the head of the queue
    *      else we acquire the lock
    *           increment the combiner pass count
    *           scan the whole queue
    *           while scanning,
    *               if the action of a node, is not null,
    *                   apply it, null their action and set their combine pass count
    *               else skip it
    *           after scanning
    *               if the combine pass count exceeds the threshold,
    *                   scan the queue, looking for unused nodes, and unlink them, ensure we don't modify from the head
    *           unlock the lock
    *               if our action has not been applied after we became the combiner, it means our node was removed by a previous combiner ,continue spinning
    * */
    public <R> R combine(Action<E, R> action, IdleStrategy strategy) {
        Node<E, R> node = (Node<E, R>) local.get();
        var stateful = node.statefulAction; //Stateful action reference is always visible due to final field visibility guarantees
        stateful.setActionAndNullResult(action);  //Volatile write to action ensures result is always null before we enqueue
        enqueueIfInactive(node);

        int idleCount = 0;
        assert stateful.action != null : "Action must not be null";
        while (stateful.action != null){

            if (lock.tryLock()){
                try {
                    scanCombineApply();
                    if(stateful.action != null) {
                        //Enqueue then apply?
                        enqueueIfInactive(node);
                        node.setAge(count);
                        node.statefulAction.applyAndNullAction(e);
                    }
                    return stateful.result; //ensure we check before we return in the case a combiner removed our node before we acquired the lock and became the combiner
                    //assertNotInQueue(node); //The only reason why we wouldn't have applied is if we were not in queue
                }finally {
                    lock.unlock();
                }
            }

            idleCount = strategy.idle(idleCount);
            enqueueIfInactive(node);

        }

        return stateful.result;
    }

    @SuppressWarnings("unchecked")
    <R>void enqueueIfInactive(Node<E, R> node){
        //Ensure to always check in the loop, just to prevent a situation where we think we're still active but we're not
        if (node.statefulAction.isInactive()){
            node.statefulAction.setActive(); //This is always visible and cant be reordered because of volatile write to head.getAndSet, setTail
            Node<E, R> prevHead = (Node<E, R>) head.getAndSet(node); //Ensure we enqueue as the head before we set the tail
            node.setNext(prevHead);
            //assertInQueue(node);
        }
    }

    @SuppressWarnings("unchecked")
    void scanCombineApply(){
        Node<E, Object> seenHead = (Node<E, Object>) this.head.get(); //H head should never be null, it should either be the dummy or some other node
        Node<E, Object> node = seenHead; //Copy seen head so we don't modify it
        ++count;

        while (node != null && !node.equals(DUMMY)){ //Ensure node != null before we continue
            this.apply(node);
            node = node.next;
        }

        //We should never remove the dummy node, else NPE issues could occur
        if (seenHead != null && count % threshold == 0){ //Rather than applying whenever count > threshold lets apply when their modulo is zero
            dequeFromHead(seenHead); //Seen head can never be null
            //assertInQueue(DUMMY, seenHead); //We need to ensure dummy is always in queue
        }
    }

    //We want to only modify from the second node in the pub queue, not the head
    private void dequeFromHead(Node<E, Object> seenHead) {
        var prev = seenHead;
        var current = seenHead.next;
        StatefulAction<E, ?> statefulAction;
        while (current != null && !current.equals(DUMMY)){
            statefulAction = current.statefulAction;
            if ((count - current.age) >= threshold){
                prev.setNext(current.next);
                current.next = null;
                current = prev.next;
                statefulAction.setInactive(); //Ensure we set this as inactive after unlinking to prevent a situation in which this thread and the node owning threader overwrite their tail
                //i.e. the node owning thread, sees its inactive and, could try to set its tail to the current head , but the combiner overwrites it, now the node is unlinked forever while still being active, leading to issues
                //Set opaque is more enticing here because we dont need a fully volatile write, since the writes to the tails are volatile, preventing reordering
                continue;
            }
            prev = current;
            current = current.next;
        }
    }


    void apply(Node<E, Object> node){
        var statefulAction =  node.statefulAction;
        if (statefulAction.canApply()) {
            statefulAction.applyAndNullAction(e);
            node.setAge(count);
        }
    }


    void assertInQueue(Node<?,?> node, Node<?,?> head){
        boolean inQueue = scanQueue(node, head);
        assert inQueue : "Node should be in queue after re insert";
    }

//    void assertNotInQueue(Node<?,?> node){
//        boolean inQueue = scanQueue(node);
//        assert !inQueue : "Node should be in queue after re insert";
//    }

    private boolean scanQueue(Node<?, ?> node, Node<?, ?> head) {
        boolean inQueue = false;
        Node<?, ?> curr = head;
        while (curr != null && curr != DUMMY){
            if (curr == node) {
                inQueue = true;
                break;
            }

            while (curr.next == null) Thread.onSpinWait();
            curr = curr.next;
        }
        return inQueue;
    }

    public E e(){
        return e;
    }

}

//Things i've done to find out why this hangs under high contention, I initially narrowed it down to the "remove node if aged function"(which removes aged nodes from the head's tail downward, doesnt modify the head though), because whenever i comment it out, it stops hanging
// I added a scan queue method, which traverses the pub queue, just to assert a different points of the program if/if not a node was in the queue
// However after adding those assertions, I noticed something strange, my code wasn't hanging anymore, so I did some digging, and I again narrowed it down to the scan for the DUMMY tail node, I did immediately after the "remove node if aged function"

//I initially thought it was the volatile read from the atomic reference of the current head, but I wanted to assert my theory, so I used the "seenHead" variable (which has been present since the hanging began), but funny enough the hanging didnt continue, so right now im confused, how is a simple scan(not modifying anything) after the removal of nodes from the pubqueue
// stopping threads from hanging. Idk if this is a memory visibility issue cause note that only one thread can actually modify from the head's tail at a time, so its a bit confusing

// So to test my thought if this was a visibility issue, i removed the queue scan and explicitly added a varhandle full fence after "the remove aged node function", to test my suspicion, however that didn't stop the hanging. So this seems like a timing issue
// To assure myself it was a timing issue, i added a sleep after  "the remove aged node function" and it didnt hang?
// Also added timed spins to instrument if a thread was indeed getting stuck spinning and here's what i found, the age is increasing meaning a combiner is running but somehow, their nodes aren't being applied, one check let me check their "next value"
//# Warmup Iteration   1: HUNG - action: io.github.kusoroadeolu.txmap.benchmarks.CombinerBenchmark$$Lambda/0x00000000100551d0@6f4e8541 ,status: 1 ,age: 518557 ,in queue: false ,node: io.github.kusoroadeolu.txmap.UnboundCombiner$Node@596c6e22, next: io.github.kusoroadeolu.txmap.UnboundCombiner$Node@476c8527
//# Warmup Iteration   2: HUNG - action: io.github.kusoroadeolu.txmap.benchmarks.CombinerBenchmark$$Lambda/0x00000000100551d0@6f4e8541 ,status: 1 ,age: 93828813 ,in queue: true ,node: io.github.kusoroadeolu.txmap.UnboundCombiner$Node@7181123d, next: io.github.kusoroadeolu.txmap.UnboundCombiner$Node@5e4e58a3
//HUNG - action: io.github.kusoroadeolu.txmap.benchmarks.CombinerBenchmark$$Lambda/0x00000000100551d0@6f4e8541 ,status: 1 ,age: 94637252 ,in queue: true ,node: io.github.kusoroadeolu.txmap.UnboundCombiner$Node@7181123d, next: null
//HUNG - action: io.github.kusoroadeolu.txmap.benchmarks.CombinerBenchmark$$Lambda/0x00000000100551d0@6f4e8541 ,status: 1 ,age: 95957471 ,in queue: true ,node: io.github.kusoroadeolu.txmap.UnboundCombiner$Node@5e4e58a3, next: io.github.kusoroadeolu.txmap.UnboundCombiner$Node@7181123d
//HUNG - action: io.github.kusoroadeolu.txmap.benchmarks.CombinerBenchmark$$Lambda/0x00000000100551d0@6f4e8541 ,status: 1 ,age: 59416478 ,in queue: false ,node: io.github.kusoroadeolu.txmap.UnboundCombiner$Node@22532028, next: null


// Ok it was a starvation issue, so i fixed by just forcing combiners to always enqueue and apply their actions no matter what, rather than just re-enqueuing and going back to spin and trusting others to apply it,but honestly i actually cant still reason about what cause the livelock and why timing even mattered in this scenario?
