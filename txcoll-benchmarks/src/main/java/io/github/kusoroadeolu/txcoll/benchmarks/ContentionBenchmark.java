package io.github.kusoroadeolu.txcoll.benchmarks;

import io.github.kusoroadeolu.txcoll.TransactionalMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Contended key throughput benchmark — multiple threads, small shared key pool.
 *
 * Goal: measure throughput and abort rate when conflicts are frequent.
 * All threads compete over a fixed pool of 4 keys so collisions are inevitable.
 *
 * Three contention profiles:
 *  - Read heavy  (90% get, 10% put)
 *  - Balanced    (50% get, 50% put)
 *  - Write heavy (10% get, 90% put)
 *
 * What to look for:
 *  - How throughput degrades as thread count and write ratio increase
 *  - Abort rate via @AuxCounters — high aborts = lots of wasted work
 *  - Whether write-heavy + high threads causes throughput to collapse
 *
 *  Some issues right now:
 *  Aborts don't throw so I probably need to add callbacks for that
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class ContentionBenchmark {

    // Small fixed key pool — all threads compete over these
    private static final String[] KEYS = {"key-0", "key-1", "key-2", "key-3"};

    private TransactionalMap<String, Integer> txMap;

    // -------------------------------------------------------------------------
    // Abort tracking via AuxCounters
    // Each thread tracks its own abort count — JMH aggregates across threads
    // -------------------------------------------------------------------------

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.EVENTS)
    public static class AbortCounters {
        public long aborts;   // JMH will report this as an additional metric
    }

    @State(Scope.Thread)
    public static class ThreadState {
        // Simple round-robin index for key selection — spreads load evenly
        int keyIndex = 0;
        int opIndex  = 0;   // Used to decide read vs write based on ratio

        String nextKey() {
            String key = KEYS[keyIndex % KEYS.length];
            keyIndex++;
            return key;
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        txMap = TransactionalMap.create();
        // Pre-populate all keys so removes and gets have something to work with
        try (var tx = txMap.beginTx()) {
            for (String key : KEYS) tx.put(key, 0);
            tx.commit();
        }
    }

    // -------------------------------------------------------------------------
    // Read heavy — 90% get, 10% put
    // -------------------------------------------------------------------------

    @Benchmark
    @Threads(1)
    public void readHeavy_1thread(ThreadState ts, AbortCounters counters, Blackhole bh) {
        readHeavy(ts, counters, bh);
    }

    @Benchmark
    @Threads(2)
    public void readHeavy_2threads(ThreadState ts, AbortCounters counters, Blackhole bh) {
        readHeavy(ts, counters, bh);
    }

    @Benchmark
    @Threads(4)
    public void readHeavy_4threads(ThreadState ts, AbortCounters counters, Blackhole bh) {
        readHeavy(ts, counters, bh);
    }

    @Benchmark
    @Threads(8)
    public void readHeavy_8threads(ThreadState ts, AbortCounters counters, Blackhole bh) {
        readHeavy(ts, counters, bh);
    }

    // -------------------------------------------------------------------------
    // Balanced — 50% get, 50% put
    // -------------------------------------------------------------------------

    @Benchmark
    @Threads(1)
    public void balanced_1thread(ThreadState ts, AbortCounters counters, Blackhole bh) {
        balanced(ts, counters, bh);
    }

    @Benchmark
    @Threads(2)
    public void balanced_2threads(ThreadState ts, AbortCounters counters, Blackhole bh) {
        balanced(ts, counters, bh);
    }

    @Benchmark
    @Threads(4)
    public void balanced_4threads(ThreadState ts, AbortCounters counters, Blackhole bh) {
        balanced(ts, counters, bh);
    }

    @Benchmark
    @Threads(8)
    public void balanced_8threads(ThreadState ts, AbortCounters counters, Blackhole bh) {
        balanced(ts, counters, bh);
    }

    // -------------------------------------------------------------------------
    // Write heavy — 90% put, 10% get
    // -------------------------------------------------------------------------

    @Benchmark
    @Threads(1)
    public void writeHeavy_1thread(ThreadState ts, AbortCounters counters, Blackhole bh) {
        writeHeavy(ts, counters, bh);
    }

    @Benchmark
    @Threads(2)
    public void writeHeavy_2threads(ThreadState ts, AbortCounters counters, Blackhole bh) {
        writeHeavy(ts, counters, bh);
    }

    @Benchmark
    @Threads(4)
    public void writeHeavy_4threads(ThreadState ts, AbortCounters counters, Blackhole bh) {
        writeHeavy(ts, counters, bh);
    }

    @Benchmark
    @Threads(8)
    public void writeHeavy_8threads(ThreadState ts, AbortCounters counters, Blackhole bh) {
        writeHeavy(ts, counters, bh);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void readHeavy(ThreadState ts, AbortCounters counters, Blackhole bh) {
        boolean isWrite = (ts.opIndex++ % 10) == 0; // 1 in 10 ops is a write
        doOp(ts.nextKey(), isWrite, counters, bh);
    }

    private void balanced(ThreadState ts, AbortCounters counters, Blackhole bh) {
        boolean isWrite = (ts.opIndex++ % 2) == 0; // every other op is a write
        doOp(ts.nextKey(), isWrite, counters, bh);
    }

    private void writeHeavy(ThreadState ts, AbortCounters counters, Blackhole bh) {
        boolean isWrite = (ts.opIndex++ % 10) != 0; // 9 in 10 ops is a write
        doOp(ts.nextKey(), isWrite, counters, bh);
    }

    private void doOp(String key, boolean isWrite, AbortCounters counters, Blackhole bh) {
        try (var tx = txMap.beginTx()) {
            if (isWrite) {
                var future = tx.put(key, 42);
                tx.commit();
                bh.consume(future.get());
            } else {
                var future = tx.get(key);
                tx.commit();
                bh.consume(future.get());
            }
        } catch (Exception e) {
            // Transaction was aborted due to conflict — track it
            counters.aborts++;
        }
    }
}