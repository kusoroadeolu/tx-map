package io.github.kusoroadeolu.txmap.benchmarks;

import io.github.kusoroadeolu.txmap.Combiner;
import io.github.kusoroadeolu.txmap.SemaphoreCombiner;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@BenchmarkMode(Mode.Throughput)   // Switching to throughput — more intuitive for scaling analysis
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)           // All threads share one map instance — realistic
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class CombinerBenchmark {

    private Combiner<List<Integer>> combiner;
    private SemaphoreCombiner<List<Integer>> semCombiner;
    final AtomicInteger num = new AtomicInteger(-1);

    @State(Scope.Thread)
    public static class ThreadState{
        int value;

        @Setup(Level.Trial)
        public void setup(CombinerBenchmark bench) {
            value = bench.num.incrementAndGet();
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        combiner = new Combiner<>(new ArrayList<>());
        semCombiner = new SemaphoreCombiner<>(new ArrayList<>());
    }

    @Benchmark
    @Threads(1)
    public void combiner_1thread(Blackhole bh, ThreadState ts) {
        bh.consume(combiner.combine(list -> list.add(ts.value)));
    }

    @Benchmark
    @Threads(2)
    public void combiner_2threads( Blackhole bh, ThreadState ts) {
        bh.consume(combiner.combine(list -> list.add(ts.value)));

    }

    @Benchmark
    @Threads(4)
    public void combiner_4threads( Blackhole bh, ThreadState ts) {
        bh.consume(combiner.combine(list -> list.add(ts.value)));

    }

    @Benchmark
    @Threads(8)
    public void combiner_8threads(Blackhole bh, ThreadState ts) {
        bh.consume(combiner.combine(list -> list.add(ts.value)));
    }


    @Benchmark
    @Threads(1)
    public void s_combiner_1thread(Blackhole bh, ThreadState ts) {
        bh.consume(semCombiner.combine(list -> list.add(ts.value)));
    }

    @Benchmark
    @Threads(2)
    public void s_combiner_2threads( Blackhole bh, ThreadState ts) {
        bh.consume(semCombiner.combine(list -> list.add(ts.value)));

    }

    @Benchmark
    @Threads(4)
    public void s_combiner_4threads( Blackhole bh, ThreadState ts) {
        bh.consume(semCombiner.combine(list -> list.add(ts.value)));

    }

    @Benchmark
    @Threads(8)
    public void s_combiner_8threads(Blackhole bh, ThreadState ts) {
        bh.consume(semCombiner.combine(list -> list.add(ts.value)));
    }



}
