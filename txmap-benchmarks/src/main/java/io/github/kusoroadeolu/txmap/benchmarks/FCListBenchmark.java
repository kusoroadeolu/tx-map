package io.github.kusoroadeolu.txmap.benchmarks;

import io.github.kusoroadeolu.txmap.FCList;
import io.github.kusoroadeolu.txmap.FlatCombinedList;
import io.github.kusoroadeolu.txmap.TransactionalMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@BenchmarkMode(Mode.Throughput)   // Switching to throughput — more intuitive for scaling analysis
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)           // All threads share one map instance — realistic
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class FCListBenchmark {

    private FlatCombinedList<Integer> fcList;
    private List<Integer> syncList;
    final AtomicInteger num = new AtomicInteger(-1);

    @State(Scope.Thread)
    public static class ThreadState{
        int value;

        @Setup(Level.Trial)
        public void setup(FCListBenchmark bench) {
            value = bench.num.incrementAndGet();
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        fcList = new FlatCombinedList<>();
        syncList = Collections.synchronizedList(new ArrayList<>());
    }

    @Benchmark
    @Threads(1)
    public void fcList_1thread(Blackhole bh, ThreadState ts) {
        bh.consume(fcList.run(list -> list.add(ts.value)));
    }

    @Benchmark
    @Threads(2)
    public void fcList_2threads( Blackhole bh, ThreadState ts) {
        bh.consume(fcList.run(list -> list.add(ts.value)));

    }

    @Benchmark
    @Threads(4)
    public void fcList_4threads( Blackhole bh, ThreadState ts) {
        bh.consume(fcList.run(list -> list.add(ts.value)));

    }

    @Benchmark
    @Threads(8)
    public void fcList_8threads(Blackhole bh, ThreadState ts) {
        bh.consume(fcList.run(list -> list.add(ts.value)));
    }




    @Benchmark
    @Threads(1)
    public void syncList_1thread(Blackhole bh, ThreadState ts) {
        bh.consume(syncList.add(ts.value));
    }

    @Benchmark
    @Threads(2)
    public void syncList_2threads( Blackhole bh, ThreadState ts) {
        bh.consume(syncList.add(ts.value));

    }

    @Benchmark
    @Threads(4)
    public void syncList_4threads( Blackhole bh, ThreadState ts) {
        bh.consume(syncList.add(ts.value));

    }

    @Benchmark
    @Threads(8)
    public void syncList_8threads(Blackhole bh, ThreadState ts) {
        bh.consume(syncList.add(ts.value));
    }
}
