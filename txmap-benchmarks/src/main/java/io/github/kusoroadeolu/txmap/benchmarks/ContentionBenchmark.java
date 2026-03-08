package io.github.kusoroadeolu.txmap.benchmarks;

import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.txmap.TransactionalMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.AsyncProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

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
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsPrepend = {
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+DebugNonSafepoints"
})
public class ContentionBenchmark {

    // Small fixed key pool — all threads compete over these
    private static final String[] KEYS = {"key-0", "key-1", "key-2", "key-3"};

    private TransactionalMap<String, Integer> txMap;


    @State(Scope.Thread)
    //@AuxCounters(AuxCounters.Type.EVENTS)
    public static class ThreadState {
        // Simple round-robin index for key selection — spreads load evenly
        int keyIndex = 0;
        int opIndex  = 0;   // Used to decide read vs write based on ratio
        public long commits = 0;
        public long aborts  = 0;

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
        doOp(ts.nextKey(), isWrite, bh, ts);
    }

    private void balanced(ThreadState ts, Blackhole bh) {
        boolean isWrite = (ts.opIndex++ % 2) == 0; // every other op is a write
        doOp(ts.nextKey(), isWrite, bh, ts);
    }

    private void writeHeavy(ThreadState ts, Blackhole bh) {
        boolean isWrite = (ts.opIndex++ % 10) != 0; // 9 in 10 ops is a write
        doOp(ts.nextKey(), isWrite, bh, ts);
    }

    //Include size in both to measure the overhead of size ops in pessimistic, though this should have minimal effect for CoW and Snapshots
    private void doOp(String key, boolean isWrite, Blackhole bh, ThreadState ts) {
        try (var tx = txMap.beginTx()) {
            io.github.kusoroadeolu.txmap.FutureValue<Option<Integer>> future;
            if (isWrite) {
                future = tx.put(key, 42);
            } else {
                future = tx.get(key);
            }

            var future2 = tx.size();
            tx.commit();
            if (tx.isCommitted()) ts.commits++;
            else ts.aborts++;
            bh.consume(future.get());
            bh.consume(future2.get());
        }
    }

    static class JMHRunner{
        void main() throws Exception {
            Options opt = new OptionsBuilder()
                    .include(ContentionBenchmark.class.getSimpleName())
                    .addProfiler("jfr", "dir=C:\\jfr-output")
                    .build();
            new Runner(opt).run();
        }
    }
}