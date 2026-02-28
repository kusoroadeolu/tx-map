package io.github.kusoroadeolu.txmap.benchmarks;

import io.github.kusoroadeolu.txmap.CombinerType;
import io.github.kusoroadeolu.txmap.FutureValue;
import io.github.kusoroadeolu.txmap.TransactionalMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark written by Claude
 * Verified by me
 * @author Kusoro Adeolu
 * */

/**
 * Flat-combining throughput benchmarks.
 *
 * Three axes:
 *  1. Thread count scaling     — fixed ops-per-tx, vary threads (1/2/4/8)
 *  2. Ops-per-transaction      — fixed threads, vary how much work each tx does
 *
 * What to look for:
 *  - Does throughput hold up or collapse as threads increase?
 *  - Does batching more ops per tx improve amortized throughput?
 *  - Where does the combiner overhead start to outweigh the batching benefit?
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class TxMapCombinerBenchmark {

    private static final String[] KEYS = {"key-0", "key-1", "key-2", "key-3"};

    private TransactionalMap<String, Integer> txMap;

    @State(Scope.Thread)
    public static class ThreadState {
        int keyIndex = 0;

        String nextKey() {
            String key = KEYS[keyIndex % KEYS.length];
            keyIndex++;
            return key;
        }
    }

    @Setup(Level.Iteration)
    public void setup() {
        txMap = TransactionalMap.createFlatCombined(CombinerType.ARRAY);
        try (var tx = txMap.beginTx()) {
            for (String key : KEYS) tx.put(key, 0);
            tx.commit();
        }
    }

    // -------------------------------------------------------------------------
    // Axis 1: Thread count scaling — 1 op per tx, vary threads
    // Tells us: how does the combiner hold up under more contention?
    // -------------------------------------------------------------------------

    @Benchmark
    @Threads(1)
    public void threadScaling_1(ThreadState ts, Blackhole bh) {
        singleOp(ts, bh);
    }

    @Benchmark
    @Threads(2)
    public void threadScaling_2(ThreadState ts, Blackhole bh) {
        singleOp(ts, bh);
    }

    @Benchmark
    @Threads(4)
    public void threadScaling_4(ThreadState ts, Blackhole bh) {
        singleOp(ts, bh);
    }

    @Benchmark
    @Threads(8)
    public void threadScaling_8(ThreadState ts, Blackhole bh) {
        singleOp(ts, bh);
    }

    // -------------------------------------------------------------------------
    // Axis 2: Ops per transaction — fixed at 4 threads, vary batch size
    // Tells us: does doing more work per tx amortize combiner overhead?
    // -------------------------------------------------------------------------

    @Benchmark
    @Threads(4)
    public void opsPerTx_1(ThreadState ts, Blackhole bh) {
        singleOp(ts, bh);
    }

    @Benchmark
    @Threads(4)
    public void opsPerTx_3(ThreadState ts, Blackhole bh) {
        multiOp(ts, bh, 3);
    }

    @Benchmark
    @Threads(4)
    public void opsPerTx_5(ThreadState ts, Blackhole bh) {
        multiOp(ts, bh, 5);
    }

    @Benchmark
    @Threads(4)
    public void opsPerTx_10(ThreadState ts, Blackhole bh) {
        multiOp(ts, bh, 10);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Single put + size per transaction — baseline unit of work */
    private void singleOp(ThreadState ts, Blackhole bh) {
        try (var tx = txMap.beginTx()) {
            var f1 = tx.put(ts.nextKey(), 42);
            var f2 = tx.size();
            tx.commit();
            bh.consume(f1.get());
            bh.consume(f2.get());
        }
    }

    /** N puts + a size check per transaction — tests amortization */
    private void multiOp(ThreadState ts, Blackhole bh, int n) {
        FutureValue<?>[] futures = new FutureValue[n + 1];
        try (var tx = txMap.beginTx()) {
            for (int i = 0; i < n; i++) {
                futures[i] = tx.put(ts.nextKey(), i);
            }
            futures[n] = tx.size();
            tx.commit();
            for (FutureValue<?> future : futures) bh.consume(future.get()); //Could probably use an array here
        }
    }
}