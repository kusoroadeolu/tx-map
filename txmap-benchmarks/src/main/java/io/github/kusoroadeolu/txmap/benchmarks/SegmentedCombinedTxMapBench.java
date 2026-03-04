package io.github.kusoroadeolu.txmap.benchmarks;

import io.github.kusoroadeolu.txmap.Combiner;
import io.github.kusoroadeolu.txmap.CombinerType;
import io.github.kusoroadeolu.txmap.FutureValue;
import io.github.kusoroadeolu.txmap.TransactionalMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
//Using spin loop strat as our default because from the raw combiner thrpt bench, it showed consistent throughput while having the best variance across all combiner types
// Here we're testing if segmenting combiners per key improves thrpt while having a good variance of ~11-12%, rather than the actual thrpt of each combiner per key matters, though that might play a factor, hence we're testing all combiners with this
// Using a synchronized combiner(basically a locked) combiner per key as a baseline

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2)
public class SegmentedCombinedTxMapBench {
    private static final String[] KEYS = {"key-0", "key-1", "key-2", "key-3"};

    private TransactionalMap<String, Integer> txMap;

    @Param({"array", "unbound", "nc", "sync"})
    private String combinerType;

    private int capacity = 100;

    private static final Combiner.IdleStrategy strategy = Combiner.IdleStrategy.spinLoop(256);

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
        txMap = switch (combinerType) {
            case "array" -> TransactionalMap.createSegmentedCombined(CombinerType.ARRAY, strategy);
            case "unbound" -> TransactionalMap.createSegmentedCombined(CombinerType.UNBOUND, strategy);
            case "nc" -> TransactionalMap.createSegmentedCombined(CombinerType.NODE_CYCLING, strategy);
            case "sync" -> TransactionalMap.createSegmentedCombined(CombinerType.SYNC, strategy);
            default -> throw new IllegalStateException("Unexpected value: " + combinerType);
        };

        try (var tx = txMap.beginTx()) {
            for (String key : KEYS) tx.put(key, 0);
            tx.commit();
        }
    }

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
