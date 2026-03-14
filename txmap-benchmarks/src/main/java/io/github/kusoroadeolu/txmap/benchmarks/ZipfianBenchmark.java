package io.github.kusoroadeolu.txmap.benchmarks;

import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.txmap.FutureValue;
import io.github.kusoroadeolu.txmap.TransactionalMap;
import io.github.kusoroadeolu.txmap.txkeeper.VersionChainType;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsPrepend = {
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+DebugNonSafepoints"
})
public class ZipfianBenchmark {

    private static final int KEY_SPACE = 1_000;  // Total number of distinct keys


    private static final int ZIPF_POOL_SIZE = 1 << 16; // 65536 samples

    private TransactionalMap<String, Integer> txMap;

    // Pre-built key pools for each theta — generated once at setup
    private String[] pool_low;    // θ = 0.5
    private String[] pool_high;   // θ = 0.9


    @Param({"queue", "nav"})
    private String versionChainType;

    static String[] buildZipfPool(double theta) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Build CDF
        double[] cdf = new double[ZipfianBenchmark.KEY_SPACE];
        double h = 0.0;
        for (int i = 1; i <= ZipfianBenchmark.KEY_SPACE; i++) h += 1.0 / Math.pow(i, theta);
        double cumulative = 0.0;
        for (int i = 0; i < ZipfianBenchmark.KEY_SPACE; i++) {
            cumulative += (1.0 / Math.pow(i + 1, theta)) / h;
            cdf[i] = cumulative;
        }

        String[] pool = new String[ZipfianBenchmark.ZIPF_POOL_SIZE];
        for (int s = 0; s < ZipfianBenchmark.ZIPF_POOL_SIZE; s++) {
            double u = rng.nextDouble();
            int lo = 0, hi = ZipfianBenchmark.KEY_SPACE - 1;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (cdf[mid] < u) lo = mid + 1;
                else hi = mid;
            }
            pool[s] = "key-" + lo;
        }
        return pool;
    }

    @Setup(Level.Trial)

    public void setup() {
        txMap = switch (versionChainType){
            case "queue" -> TransactionalMap.create(VersionChainType.QUEUE);
            case "nav" -> TransactionalMap.create(VersionChainType.NAVIGABLE);
            default -> throw new IllegalArgumentException();
        };

        // Pre-seed all keys so reads don't trivially return empty
        try (var tx = txMap.beginTx()) {
            for (int i = 0; i < KEY_SPACE; i++) tx.put("key-" + i, i);
            tx.commit();
        }

        pool_low  = buildZipfPool(0.5);
        pool_high = buildZipfPool(0.9);
    }

    // -------------------------------------------------------------------------
    // Thread state — each thread tracks its own position in the key pool
    // -------------------------------------------------------------------------

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.EVENTS)
    public static class ThreadState {
        int poolIndex = 0;
        public long commits = 0;
        public long aborts  = 0;

        String nextKey(String[] pool) {
            String key = pool[poolIndex & (ZIPF_POOL_SIZE - 1)];
            poolIndex++;
            return key;
        }
    }

    // -------------------------------------------------------------------------
    // Low skew (θ = 0.5) — read heavy
    // -------------------------------------------------------------------------

    @Benchmark @Threads(1)
    public void lowSkew_readHeavy_1thread(ThreadState ts, Blackhole bh)  { readHeavy(pool_low, ts, bh); }

    @Benchmark @Threads(2)
    public void lowSkew_readHeavy_2threads(ThreadState ts, Blackhole bh) { readHeavy(pool_low, ts, bh); }

    @Benchmark @Threads(4)
    public void lowSkew_readHeavy_4threads(ThreadState ts, Blackhole bh) { readHeavy(pool_low, ts, bh); }

    @Benchmark @Threads(8)
    public void lowSkew_readHeavy_8threads(ThreadState ts, Blackhole bh) { readHeavy(pool_low, ts, bh); }

    // -------------------------------------------------------------------------
    // Low skew (θ = 0.5) — write heavy
    // -------------------------------------------------------------------------

    @Benchmark @Threads(1)
    public void lowSkew_writeHeavy_1thread(ThreadState ts, Blackhole bh)  { writeHeavy(pool_low, ts, bh); }

    @Benchmark @Threads(2)
    public void lowSkew_writeHeavy_2threads(ThreadState ts, Blackhole bh) { writeHeavy(pool_low, ts, bh); }

    @Benchmark @Threads(4)
    public void lowSkew_writeHeavy_4threads(ThreadState ts, Blackhole bh) { writeHeavy(pool_low, ts, bh); }

    @Benchmark @Threads(8)
    public void lowSkew_writeHeavy_8threads(ThreadState ts, Blackhole bh) { writeHeavy(pool_low, ts, bh); }

    // -------------------------------------------------------------------------
    // High skew (θ = 0.9) — read heavy
    // -------------------------------------------------------------------------

    @Benchmark @Threads(1)
    public void highSkew_readHeavy_1thread(ThreadState ts, Blackhole bh)  { readHeavy(pool_high, ts, bh); }

    @Benchmark @Threads(2)
    public void highSkew_readHeavy_2threads(ThreadState ts, Blackhole bh) { readHeavy(pool_high, ts, bh); }

    @Benchmark @Threads(4)
    public void highSkew_readHeavy_4threads(ThreadState ts, Blackhole bh) { readHeavy(pool_high, ts, bh); }

    @Benchmark @Threads(8)
    public void highSkew_readHeavy_8threads(ThreadState ts, Blackhole bh) { readHeavy(pool_high, ts, bh); }

    // -------------------------------------------------------------------------
    // High skew (θ = 0.9) — write heavy
    // -------------------------------------------------------------------------

    @Benchmark @Threads(1)
    public void highSkew_writeHeavy_1thread(ThreadState ts, Blackhole bh)  { writeHeavy(pool_high, ts, bh); }

    @Benchmark @Threads(2)
    public void highSkew_writeHeavy_2threads(ThreadState ts, Blackhole bh) { writeHeavy(pool_high, ts, bh); }

    @Benchmark @Threads(4)
    public void highSkew_writeHeavy_4threads(ThreadState ts, Blackhole bh) { writeHeavy(pool_high, ts, bh); }

    @Benchmark @Threads(8)
    public void highSkew_writeHeavy_8threads(ThreadState ts, Blackhole bh) { writeHeavy(pool_high, ts, bh); }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void readHeavy(String[] pool, ThreadState ts, Blackhole bh) {
        boolean isWrite = (ts.poolIndex % 10) == 0; // 10% writes
        doOp(ts.nextKey(pool), isWrite, ts, bh);
    }

    private void writeHeavy(String[] pool, ThreadState ts, Blackhole bh) {
        boolean isWrite = (ts.poolIndex % 10) != 0; // 90% writes
        doOp(ts.nextKey(pool), isWrite, ts, bh);
    }

    private void doOp(String key, boolean isWrite, ThreadState ts, Blackhole bh) {
        var tx = txMap.beginTx();
            FutureValue<Integer> future;
            do {
                if (isWrite) future = tx.put(key, 42);
                else         future = tx.get(key);
                tx.commit();
                if (tx.isCommitted()) ts.commits++;
                else if(tx.isAborted()) ts.aborts++;
            }while (tx.isAborted());
            bh.consume(future.get());
    }

    static class JMHRunner {
        void main() throws Exception {
            Options opt = new OptionsBuilder()
                    .include(ZipfianBenchmark.class.getSimpleName())
                    .addProfiler("jfr", "dir=C:\\jfr-output-zipf")
                    .build();
            new Runner(opt).run();
        }
    }
}