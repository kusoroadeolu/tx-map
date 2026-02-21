package io.github.kusoroadeolu.txmap.benchmarks;



import io.github.kusoroadeolu.txmap.TransactionalMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


/**
 * Baseline benchmark — single threaded, zero contention.
 *
 * Goal: measure the raw overhead of the transaction machinery vs a plain
 * ConcurrentHashMap. No lock competition, no aborts. Just the floor cost.
 *
 * Run with:
 *   java -jar benchmarks.jar BaselineBenchmark -rf json -rff results.json
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)           // Each thread gets its own state — no sharing
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)                       // Two fresh JVM forks to reduce JIT noise
public class BaselineBenchmark {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private TransactionalMap<String, Integer> txMap;
    private ConcurrentHashMap<String, Integer> rawMap;

    // Pre-built keys so we're not paying String allocation cost in hot path
    private static final String KEY_A = "key-a";
    private static final String KEY_B = "key-b";
    private static final String KEY_C = "key-c";

    @Setup(Level.Trial)
    public void setup() {
        txMap = TransactionalMap.createSnapshot();
        rawMap = new ConcurrentHashMap<>();

        // Pre-populate so get/remove benchmarks have something to work with
        try (var tx = txMap.beginTx()) {
            tx.put(KEY_A, 1);
            tx.put(KEY_B, 2);
            tx.put(KEY_C, 3);
            tx.commit();
        }

        rawMap.put(KEY_A, 1);
        rawMap.put(KEY_B, 2);
        rawMap.put(KEY_C, 3);
    }

    // -------------------------------------------------------------------------
    // Single op benchmarks — txMap
    // -------------------------------------------------------------------------

    @Benchmark
    public void txMap_put(Blackhole bh) {
        try (var tx = txMap.beginTx()) {
            var future = tx.put(KEY_A, 42);
            tx.commit();
            bh.consume(future.get());
        }
    }

    @Benchmark
    public void txMap_get(Blackhole bh) {
        try (var tx = txMap.beginTx()) {
            var future = tx.get(KEY_A);
            tx.commit();
            bh.consume(future.get());
        }
    }

    @Benchmark
    public void txMap_containsKey(Blackhole bh) {
        try (var tx = txMap.beginTx()) {
            var future = tx.containsKey(KEY_A);
            tx.commit();
            bh.consume(future.get());
        }
    }

    @Benchmark
    public void txMap_size(Blackhole bh) {
        try (var tx = txMap.beginTx()) {
            var future = tx.size();
            tx.commit();
            bh.consume(future.get());
        }
    }

    @Benchmark
    public void txMap_remove(Blackhole bh) {
        // Put then remove in same tx so the map stays stable across iterations
        try (var tx = txMap.beginTx()) {
            tx.put(KEY_C, 99);
            var future = tx.remove(KEY_C);
            tx.commit();
            bh.consume(future.get());
        }
    }

    // -------------------------------------------------------------------------
    // Batch tx benchmark — txMap (3 ops in one transaction)
    // -------------------------------------------------------------------------

    @Benchmark
    public void txMap_batch_putGetContains(Blackhole bh) {
        try (var tx = txMap.beginTx()) {
            var put     = tx.put(KEY_B, 10);
            var get     = tx.get(KEY_A);
            var contains = tx.containsKey(KEY_C);
            tx.commit();
            bh.consume(put.get());
            bh.consume(get.get());
            bh.consume(contains.get());
        }
    }

    // -------------------------------------------------------------------------
    // Equivalent raw ConcurrentHashMap benchmarks — the baseline ceiling
    // -------------------------------------------------------------------------

    @Benchmark
    public void rawMap_put(Blackhole bh) {
        bh.consume(rawMap.put(KEY_A, 42));
    }

    @Benchmark
    public void rawMap_get(Blackhole bh) {
        bh.consume(rawMap.get(KEY_A));
    }

    @Benchmark
    public void rawMap_containsKey(Blackhole bh) {
        bh.consume(rawMap.containsKey(KEY_A));
    }

    @Benchmark
    public void rawMap_size(Blackhole bh) {
        bh.consume(rawMap.size());
    }

    @Benchmark
    public void rawMap_remove(Blackhole bh) {
        rawMap.put(KEY_C, 99);
        bh.consume(rawMap.remove(KEY_C));
    }

    @Benchmark
    public void rawMap_batch_putGetContains(Blackhole bh) {
        bh.consume(rawMap.put(KEY_B, 10));
        bh.consume(rawMap.get(KEY_A));
        bh.consume(rawMap.containsKey(KEY_C));
    }
}