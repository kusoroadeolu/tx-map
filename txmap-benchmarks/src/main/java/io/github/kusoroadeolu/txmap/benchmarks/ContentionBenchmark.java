package io.github.kusoroadeolu.txmap.benchmarks;

import io.github.kusoroadeolu.txmap.FutureValue;
import io.github.kusoroadeolu.txmap.MapTransaction;
import io.github.kusoroadeolu.txmap.TransactionalMap;
import io.github.kusoroadeolu.txmap.txkeeper.VersionChainType;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsPrepend = {
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+DebugNonSafepoints"
})
public class ContentionBenchmark {

    private static final String[] KEYS = {"key-0", "key-1", "key-2", "key-3"};

    @Param({"queue", "nav"})
    private String versionChainType;

    private TransactionalMap<String, Integer> txMap;



    @State(Scope.Thread)
    //@AuxCounters(AuxCounters.Type.EVENTS)
    public static class ThreadState {
        int keyIndex = 0;
        int opIndex  = 0;   // Used to decide read vs write based on ratio
        public long commits = 0;
        public long aborts  = 0;
        public Random random;

        String nextKey() {
            String key = KEYS[keyIndex % KEYS.length];
            keyIndex++;
            return key;
        }


    }

    @Setup(Level.Trial)
    public void setup() {
        txMap = switch (versionChainType){
            case "queue" -> TransactionalMap.create(VersionChainType.QUEUE);
            case "nav" -> TransactionalMap.create(VersionChainType.NAVIGABLE);
            default -> throw new IllegalArgumentException();
        };
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


    private void writeHeavy(ThreadState ts, Blackhole bh) {
        boolean isWrite = (ts.opIndex++ % 10) != 0; // 9 in 10 ops is a write
        doOp(ts.nextKey(), isWrite, bh, ts);
    }

    //Included size in both to measure the overhead of size ops in pessimistic, though this should have minimal effect for CoW and Snapshots
    private void doOp(String key, boolean isWrite, Blackhole bh, ThreadState ts) {
        MapTransaction<String, Integer> tx;
        FutureValue<Integer> future;
        FutureValue<Integer> future2;
            tx = txMap.beginTx();
            if (isWrite) {
                future = tx.put(key, 42);
            } else {
                future = tx.get(key);
            }
            future2 = tx.size();

            tx.commit();
            if (tx.isCommitted()) ts.commits++;
            else ts.aborts++;

            bh.consume(future.get());
            bh.consume(future2.get());
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