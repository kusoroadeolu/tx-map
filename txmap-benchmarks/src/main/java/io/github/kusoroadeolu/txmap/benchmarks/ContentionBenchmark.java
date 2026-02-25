package io.github.kusoroadeolu.txmap.benchmarks;

import io.github.kusoroadeolu.txmap.TransactionalMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Contended key throughput benchmark — multiple threads, small shared key pool.
 *
 * Goal: measure throughput when conflicts are frequent.
 * All threads compete over a fixed pool of 4 keys so collisions are inevitable.
 *
 * Three contention profiles:
 *  - Read heavy  (90% get, 10% put)
 *  - Balanced    (50% get, 50% put)
 *  - Write heavy (10% get, 90% put)
 *
 * What to look for:
 *  - How throughput degrades as thread count and write ratio increase
 *  - Whether write-heavy + high threads causes throughput to collapse
 */
/*
* Benchmark                                 Mode  Cnt        Score        Error  Units
ContentionBenchmark.balanced_1thread     thrpt   10   867721.961 ± 315544.095  ops/s
ContentionBenchmark.balanced_2threads    thrpt   10  1022766.460 ± 196795.874  ops/s
ContentionBenchmark.balanced_4threads    thrpt   10   650645.544 ± 203857.492  ops/s
ContentionBenchmark.balanced_8threads    thrpt   10   498178.960 ± 187042.209  ops/s
ContentionBenchmark.readHeavy_1thread    thrpt   10  1266642.451 ± 304989.799  ops/s
ContentionBenchmark.readHeavy_2threads   thrpt   10  1618470.600 ± 139661.272  ops/s
ContentionBenchmark.readHeavy_4threads   thrpt   10   999253.579 ±  28831.378  ops/s
ContentionBenchmark.readHeavy_8threads   thrpt   10   701199.276 ± 236488.421  ops/s
ContentionBenchmark.writeHeavy_1thread   thrpt   10   883039.762 ±  91229.210  ops/s
ContentionBenchmark.writeHeavy_2threads  thrpt   10   969683.192 ± 403437.561  ops/s
ContentionBenchmark.writeHeavy_4threads  thrpt   10   376637.494 ±  68776.718  ops/s
ContentionBenchmark.writeHeavy_8threads  thrpt   10   340917.224 ±  77466.989  ops/s
*
* */
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
        txMap = TransactionalMap.createFlatCombined();
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
    public void readHeavy_1thread(ThreadState ts, Blackhole bh) {
        readHeavy(ts, bh);
    }

    @Benchmark
    @Threads(2)
    public void readHeavy_2threads(ThreadState ts, Blackhole bh) {
        readHeavy(ts, bh);
    }

    @Benchmark
    @Threads(4)
    public void readHeavy_4threads(ThreadState ts, Blackhole bh) {
        readHeavy(ts, bh);
    }

    @Benchmark
    @Threads(8)
    public void readHeavy_8threads(ThreadState ts, Blackhole bh) {
        readHeavy(ts, bh);
    }

//    // -------------------------------------------------------------------------
//    // Balanced — 50% get, 50% put
//    // -------------------------------------------------------------------------
//
//    @Benchmark
//    @Threads(1)
//    public void balanced_1thread(ThreadState ts, Blackhole bh) {
//        balanced(ts, bh);
//    }
//
//    @Benchmark
//    @Threads(2)
//    public void balanced_2threads(ThreadState ts , Blackhole bh) {
//        balanced(ts, bh);
//    }
//
//    @Benchmark
//    @Threads(4)
//    public void balanced_4threads(ThreadState ts, Blackhole bh) {
//        balanced(ts,bh);
//    }
//
//    @Benchmark
//    @Threads(8)
//    public void balanced_8threads(ThreadState ts, Blackhole bh) {
//        balanced(ts, bh);
//    }

    // -------------------------------------------------------------------------
    // Write heavy — 90% put, 10% get
    // -------------------------------------------------------------------------

    @Benchmark
    @Threads(1)
    public void writeHeavy_1thread(ThreadState ts, Blackhole bh) {
        writeHeavy(ts, bh);
    }

    @Benchmark
    @Threads(2)
    public void writeHeavy_2threads(ThreadState ts, Blackhole bh) {
        writeHeavy(ts, bh);
    }

    @Benchmark
    @Threads(4)
    public void writeHeavy_4threads(ThreadState ts, Blackhole bh) {
        writeHeavy(ts, bh);
    }

    @Benchmark
    @Threads(8)
    public void writeHeavy_8threads(ThreadState ts, Blackhole bh) {
        writeHeavy(ts, bh);
    }



    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void readHeavy(ThreadState ts, Blackhole bh) {
        boolean isWrite = (ts.opIndex++ % 10) == 0; // 1 in 10 ops is a write
        doOp(ts.nextKey(), isWrite, bh);
    }

    private void balanced(ThreadState ts, Blackhole bh) {
        boolean isWrite = (ts.opIndex++ % 2) == 0; // every other op is a write
        doOp(ts.nextKey(), isWrite, bh);
    }

    private void writeHeavy(ThreadState ts, Blackhole bh) {
        boolean isWrite = (ts.opIndex++ % 10) != 0; // 9 in 10 ops is a write
        doOp(ts.nextKey(), isWrite, bh);
    }

    //Include size in both to measure the overhead of size ops in pessimistic, though this should have minimal effect for CoW and Snapshots
    private void doOp(String key, boolean isWrite, Blackhole bh) {
        try (var tx = txMap.beginTx()) {
            if (isWrite) {
                var future = tx.put(key, 42);
                var future2 = tx.size();
                tx.commit();
                bh.consume(future.get());
                bh.consume(future2.get());
            } else {
                var future = tx.get(key);
                var future2 = tx.size();
                tx.commit();
                bh.consume(future.get());
                bh.consume(future2.get());
            }
        }
    }
}