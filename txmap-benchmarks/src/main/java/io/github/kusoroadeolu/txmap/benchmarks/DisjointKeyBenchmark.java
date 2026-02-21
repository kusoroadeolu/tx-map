package io.github.kusoroadeolu.txmap.benchmarks;

import io.github.kusoroadeolu.txmap.TransactionalMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Disjoint key throughput benchmark — multiple threads, zero key overlap.
 *
 * Goal: verify the implementation scales with parallelism when there's no
 * reason for contention. Each thread operates exclusively on its own key
 * so no aborts should occur.
 *
 * What to look for:
 *  - Throughput should scale close to linearly with thread count
 *  - If it doesn't, shared state (KeyToLockers, SynchronizedTxSet) is bottlenecking
 *  - Gap between txMap and rawMap reveals parallelism overhead of tx machinery
 *
 * Run with:
 *   java -jar benchmarks.jar DisjointKeyBenchmark -rf json -rff results.json
 */
@BenchmarkMode(Mode.Throughput)   // Switching to throughput — more intuitive for scaling analysis
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)           // All threads share one map instance — realistic
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class DisjointKeyBenchmark {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private TransactionalMap<String, Integer> txMap;
    private ConcurrentHashMap<String, Integer> rawMap;

    // Assign each thread a unique index for key isolation
    private final AtomicInteger threadCounter = new AtomicInteger(0);

    @State(Scope.Thread)
    public static class ThreadState {
        String key;          // This thread's dedicated key — never touched by others
        int threadIndex;

        @Setup(Level.Trial)
        public void setup(DisjointKeyBenchmark bench) {
            this.threadIndex = bench.threadCounter.getAndIncrement();
            this.key = "thread-key-" + threadIndex;
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        txMap = TransactionalMap.createSnapshot();
        rawMap = new ConcurrentHashMap<>();
        threadCounter.set(0);
    }

    // -------------------------------------------------------------------------
    // Single op — txMap
    // -------------------------------------------------------------------------

    @Benchmark
    @Threads(1)
    public void txMap_put_1thread(ThreadState ts, Blackhole bh) {
        txMap_put(ts, bh);
    }

    @Benchmark
    @Threads(2)
    public void txMap_put_2threads(ThreadState ts, Blackhole bh) {
        txMap_put(ts, bh);
    }

    @Benchmark
    @Threads(4)
    public void txMap_put_4threads(ThreadState ts, Blackhole bh) {
        txMap_put(ts, bh);
    }

    @Benchmark
    @Threads(8)
    public void txMap_put_8threads(ThreadState ts, Blackhole bh) {
        txMap_put(ts, bh);
    }

    @Benchmark
    @Threads(16)
    public void txMap_put_16threads(ThreadState ts, Blackhole bh) {
        txMap_put(ts, bh);
    }

    // -------------------------------------------------------------------------
    // Batch op (put + get + containsKey) — txMap
    // -------------------------------------------------------------------------

    @Benchmark
    @Threads(1)
    public void txMap_batch_1thread(ThreadState ts, Blackhole bh) {
        txMap_batch(ts, bh);
    }

    @Benchmark
    @Threads(2)
    public void txMap_batch_2threads(ThreadState ts, Blackhole bh) {
        txMap_batch(ts, bh);
    }

    @Benchmark
    @Threads(4)
    public void txMap_batch_4threads(ThreadState ts, Blackhole bh) {
        txMap_batch(ts, bh);
    }

    @Benchmark
    @Threads(8)
    public void txMap_batch_8threads(ThreadState ts, Blackhole bh) {
        txMap_batch(ts, bh);
    }

    @Benchmark
    @Threads(16)
    public void txMap_batch_16threads(ThreadState ts, Blackhole bh) {
        txMap_batch(ts, bh);
    }

//    // -------------------------------------------------------------------------
//    // Equivalent rawMap benchmarks
//    // -------------------------------------------------------------------------
//
//    @Benchmark
//    @Threads(1)
//    public void rawMap_put_1thread(ThreadState ts, Blackhole bh) {
//        bh.consume(rawMap.put(ts.key, 42));
//    }
//
//    @Benchmark
//    @Threads(2)
//    public void rawMap_put_2threads(ThreadState ts, Blackhole bh) {
//        bh.consume(rawMap.put(ts.key, 42));
//    }
//
//    @Benchmark
//    @Threads(4)
//    public void rawMap_put_4threads(ThreadState ts, Blackhole bh) {
//        bh.consume(rawMap.put(ts.key, 42));
//    }
//
//    @Benchmark
//    @Threads(8)
//    public void rawMap_put_8threads(ThreadState ts, Blackhole bh) {
//        bh.consume(rawMap.put(ts.key, 42));
//    }
//
//    @Benchmark
//    @Threads(16)
//    public void rawMap_put_16threads(ThreadState ts, Blackhole bh) {
//        bh.consume(rawMap.put(ts.key, 42));
//    }
//
//    @Benchmark
//    @Threads(1)
//    public void rawMap_batch_1thread(ThreadState ts, Blackhole bh) {
//        rawMap_batch(ts, bh);
//    }
//
//    @Benchmark
//    @Threads(2)
//    public void rawMap_batch_2threads(ThreadState ts, Blackhole bh) {
//        rawMap_batch(ts, bh);
//    }
//
//    @Benchmark
//    @Threads(4)
//    public void rawMap_batch_4threads(ThreadState ts, Blackhole bh) {
//        rawMap_batch(ts, bh);
//    }
//
//    @Benchmark
//    @Threads(8)
//    public void rawMap_batch_8threads(ThreadState ts, Blackhole bh) {
//        rawMap_batch(ts, bh);
//    }
//
//    @Benchmark
//    @Threads(16)
//    public void rawMap_batch_16threads(ThreadState ts, Blackhole bh) {
//        rawMap_batch(ts, bh);
//    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void txMap_put(ThreadState ts, Blackhole bh) {
        try (var tx = txMap.beginTx()) {
            var future = tx.put(ts.key, 42);
            tx.commit();
            bh.consume(future.get());
        }
    }

    private void txMap_batch(ThreadState ts, Blackhole bh) {
        try (var tx = txMap.beginTx()) {
            var put      = tx.put(ts.key, 10);
            var get      = tx.get(ts.key);
            var contains = tx.containsKey(ts.key);
            tx.commit();
            bh.consume(put.get());
            bh.consume(get.get());
            bh.consume(contains.get());
        }
    }

    private void rawMap_batch(ThreadState ts, Blackhole bh) {
        bh.consume(rawMap.put(ts.key, 10));
        bh.consume(rawMap.get(ts.key));
        bh.consume(rawMap.containsKey(ts.key));
    }
}