package io.github.kusoroadeolu.txmap.benchmarks;

import io.github.kusoroadeolu.txmap.AtomicArrayCombiner;
import io.github.kusoroadeolu.txmap.Combiner;
import io.github.kusoroadeolu.txmap.SemaphoreCombiner;
import io.github.kusoroadeolu.txmap.UnboundCombiner;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@BenchmarkMode(Mode.Throughput)   // Switching to throughput — more intuitive for scaling analysis
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)           // All threads share one map instance — realistic
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2)
public class CombinerBenchmark {

    private Combiner<IntAdder> atomicArrCombiner;
    private Combiner<IntAdder> unboundCombiner;
    private SemaphoreCombiner<IntAdder> semCombiner;

    @Setup(Level.Trial)
    public void setup() {
        unboundCombiner = new UnboundCombiner<>(new IntAdder());
        atomicArrCombiner = new AtomicArrayCombiner<>(new IntAdder());
        semCombiner = new SemaphoreCombiner<>(new IntAdder());
    }

    @Benchmark
    @Threads(1)
    public void arr_combiner_1thread(Blackhole bh) {
        bh.consume(atomicArrCombiner.combine(IntAdder::incrementAndGet));
    }

    @Benchmark
    @Threads(2)
    public void arr_combiner_2threads( Blackhole bh) {
        bh.consume(atomicArrCombiner.combine(IntAdder::incrementAndGet));

    }

    @Benchmark
    @Threads(4)
    public void arr_combiner_4threads( Blackhole bh) {
        bh.consume(atomicArrCombiner.combine(IntAdder::incrementAndGet));

    }

    @Benchmark
    @Threads(8)
    public void arr_combiner_8threads(Blackhole bh) {
        bh.consume(atomicArrCombiner.combine(IntAdder::incrementAndGet));
    }

//    @Benchmark
//    @Threads(1)
//    public void unb_combiner_1thread(Blackhole bh) {
//        bh.consume(unboundCombiner.combine(IntAdder::incrementAndGet));
//    }
//
//    @Benchmark
//    @Threads(2)
//    public void unb_combiner_2threads( Blackhole bh) {
//        bh.consume(unboundCombiner.combine(IntAdder::incrementAndGet));
//
//    }
//
//    @Benchmark
//    @Threads(4)
//    public void unb_combiner_4threads( Blackhole bh) {
//        bh.consume(unboundCombiner.combine(IntAdder::incrementAndGet));
//
//    }
//
//    @Benchmark
//    @Threads(8)
//    public void unb_combiner_8threads(Blackhole bh) {
//        bh.consume(unboundCombiner.combine(IntAdder::incrementAndGet));
//    }
//
//
//    @Benchmark
//    @Threads(1)
//    public void sem_combiner_1thread(Blackhole bh) {
//        bh.consume(semCombiner.combine(IntAdder::incrementAndGet));
//    }
//
//    @Benchmark
//    @Threads(2)
//    public void sem_combiner_2threads( Blackhole bh) {
//        bh.consume(semCombiner.combine(IntAdder::incrementAndGet));
//
//    }
//
//    @Benchmark
//    @Threads(4)
//    public void sem_combiner_4threads( Blackhole bh) {
//        bh.consume(semCombiner.combine(IntAdder::incrementAndGet));
//    }
//
//    @Benchmark
//    @Threads(8)
//    public void sem_combiner_8threads(Blackhole bh) {
//        bh.consume(semCombiner.combine(IntAdder::incrementAndGet));
//    }

    static class IntAdder{
        int a = 0;

        int incrementAndGet(){
            return ++a;
        }
    }
}
