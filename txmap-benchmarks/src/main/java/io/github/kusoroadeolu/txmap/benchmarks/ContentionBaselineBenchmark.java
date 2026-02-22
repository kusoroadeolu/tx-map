package io.github.kusoroadeolu.txmap.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class ContentionBaselineBenchmark {
    // Small fixed key pool — all threads compete over these
    private static final String[] KEYS = {"key-0", "key-1", "key-2", "key-3"};

    private ConcurrentMap<String, Integer> map;

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
        map = new ConcurrentHashMap<>();
        // Pre-populate all keys so removes and gets have something to work with
        for (String key : KEYS) map.put(key, 0);
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
    // Balanced — 50% get, 50% put
    // -------------------------------------------------------------------------

    @Benchmark
    @Threads(1)
    public void balanced_1thread(ThreadState ts, Blackhole bh) {
        balanced(ts, bh);
    }

    @Benchmark
    @Threads(2)
    public void balanced_2threads(ThreadState ts , Blackhole bh) {
        balanced(ts, bh);
    }

    @Benchmark
    @Threads(4)
    public void balanced_4threads(ThreadState ts, Blackhole bh) {
        balanced(ts,bh);
    }

    @Benchmark
    @Threads(8)
    public void balanced_8threads(ThreadState ts, Blackhole bh) {
        balanced(ts, bh);
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

    private void doOp(String key, boolean isWrite, Blackhole bh) {
        Integer var1;
        var var2 = map.size();
        if (isWrite) {
            var1 = map.put(key, 42);
        } else {
            var1 = map.get(key);
        }
        bh.consume(var1);
        bh.consume(var2);
    }
}
