package io.github.kusoroadeolu.txmap.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class DisjointKeyBaselineBenchmark {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private ConcurrentHashMap<String, Integer> rawMap;
    private final AtomicInteger threadCounter = new AtomicInteger(0);

    // Assign each thread a unique index for key isolation

    @State(Scope.Thread)
    public static class ThreadState {
        String key;          // This thread's dedicated key — never touched by others
        int threadIndex;

        @Setup(Level.Trial)
        public void setup(DisjointKeyBaselineBenchmark bench) {
            this.threadIndex = bench.threadCounter.getAndIncrement();
            this.key = "thread-key-" + threadIndex;
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        rawMap = new ConcurrentHashMap<>();
        threadCounter.set(0);
    }


    @Benchmark
    @Threads(1)
    public void rawMap_put_1thread(ThreadState ts, Blackhole bh) {
        bh.consume(rawMap.put(ts.key, 42));
    }

    @Benchmark
    @Threads(2)
    public void rawMap_put_2threads(ThreadState ts, Blackhole bh) {
        bh.consume(rawMap.put(ts.key, 42));
    }

    @Benchmark
    @Threads(4)
    public void rawMap_put_4threads(ThreadState ts, Blackhole bh) {
        bh.consume(rawMap.put(ts.key, 42));
    }

    @Benchmark
    @Threads(8)
    public void rawMap_put_8threads(ThreadState ts, Blackhole bh) {
        bh.consume(rawMap.put(ts.key, 42));
    }

    @Benchmark
    @Threads(16)
    public void rawMap_put_16threads(ThreadState ts, Blackhole bh) {
        bh.consume(rawMap.put(ts.key, 42));
    }

    @Benchmark
    @Threads(1)
    public void rawMap_batch_1thread(ThreadState ts, Blackhole bh) {
        rawMap_batch(ts, bh);
    }

    @Benchmark
    @Threads(2)
    public void rawMap_batch_2threads(ThreadState ts, Blackhole bh) {
        rawMap_batch(ts, bh);
    }

    @Benchmark
    @Threads(4)
    public void rawMap_batch_4threads(ThreadState ts, Blackhole bh) {
        rawMap_batch(ts, bh);
    }

    @Benchmark
    @Threads(8)
    public void rawMap_batch_8threads(ThreadState ts, Blackhole bh) {
        rawMap_batch(ts, bh);
    }

    @Benchmark
    @Threads(16)
    public void rawMap_batch_16threads(ThreadState ts, Blackhole bh) {
        rawMap_batch(ts, bh);
    }

    private void rawMap_batch(ThreadState ts, Blackhole bh) {
        bh.consume(rawMap.put(ts.key, 10));
        bh.consume(rawMap.get(ts.key));
        bh.consume(rawMap.containsKey(ts.key));
    }
}
