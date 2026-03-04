package io.github.kusoroadeolu.txmap;

import java.util.concurrent.locks.LockSupport;

public interface Combiner<E> {
    <R>R combine(Action<E, R> action);

    <R>R combine(Action<E, R> action, IdleStrategy strategy);

    E e();

    @FunctionalInterface
    interface IdleStrategy {
        int idle(int idleCount);

        static IdleStrategy busySpin() {
            return ignore -> ignore;
        }

        static IdleStrategy yield(int maxSpins) {
            return idleCount -> {
                if (idleCount < maxSpins) {
                    idleCount++;
                } else {
                    Thread.yield();
                }
                return idleCount;
            };
        }

        static IdleStrategy park(int maxSpins) {
            return idleCount -> {
                if (idleCount < maxSpins) {
                    idleCount++;
                } else {
                    LockSupport.parkNanos(1);
                }
                return idleCount;
            };
        }

        static IdleStrategy spinLoop(int maxSpins) {
            return idleCount -> {
                    int i = 0;
                    while (i < maxSpins) {
                        i++;
                        Thread.onSpinWait();
                    }
                return idleCount;
            };
        }
    }


    static <E>Combiner<E> getInstance(CombinerType type, E e){
        return switch (type){
            case ARRAY -> new AtomicArrayCombiner<>(e);
            case UNBOUND -> new UnboundCombiner<>(e);
            case NODE_CYCLING -> new NodeCyclingCombiner<>(e);
            case SYNC -> new SynchronizedCombiner<>(e);
        };
    }
}
