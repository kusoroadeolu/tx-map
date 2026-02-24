package io.github.kusoroadeolu.txmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class FCList<E>{


    public interface FCAction<E, R>{
        R apply(List<E> list);
    }


    public static class StatefulFCAction<E, R>{
        final FCAction<E, R> action;
        final AtomicInteger state;
        volatile R result;
        private final static int APPLIED = 1;
        private final static int UN_APPLIED = 0;

        public StatefulFCAction(FCAction<E, R> action) {
            this.action = action;
            state = new AtomicInteger(UN_APPLIED);
        }

        void apply(List<E> list){
          this.result = action.apply(list);
          state.compareAndSet(UN_APPLIED, APPLIED);
        }

        R result(){
            return this.result;
        }

        boolean isApplied(){
            return state.get() == APPLIED;
        }
    }

    private final List<E> list;
    private final Queue<StatefulFCAction<E, ?>> publications;
    private final AtomicInteger state;
    private final static int HELD = 1;
    private final static int FREE = 0;

    public FCList() {
        this.list = new ArrayList<>();
        this.publications = new ConcurrentLinkedQueue<>();
        this.state = new AtomicInteger(FREE);
    }

    public <R>R run(FCAction<E, R> action) {
        var stateful = new StatefulFCAction<>(action);
        publications.add(stateful);
        if (state.compareAndSet(FREE, HELD)){ //Probably the bottle neck here
            StatefulFCAction<E, ?> read;
            while ((read = publications.poll()) != null) read.apply(this.list);
            state.set(FREE);
        }
        while (true){
            if (stateful.isApplied()){
                return stateful.result;
            }
            Thread.onSpinWait();
        }
    }

    public List<E> list(){
        return this.list;
    }

}
