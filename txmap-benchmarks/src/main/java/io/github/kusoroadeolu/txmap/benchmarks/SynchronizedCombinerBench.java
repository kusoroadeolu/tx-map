package io.github.kusoroadeolu.txmap.benchmarks;

import io.github.kusoroadeolu.txmap.Combiner;
import io.github.kusoroadeolu.txmap.SynchronizedCombiner;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2)
public class SynchronizedCombinerBench {

    private final static int MAX_SPINS = 256;

    private Combiner<HeavyAdder> combiner;

    @Param({"500"})
    private int tokens;

    @Setup(Level.Trial)
    public void setup(){
        combiner = new SynchronizedCombiner<>(new HeavyAdder(tokens));
    }


    @Benchmark
    @Threads(4)
    public void combiner_4threads( Blackhole bh) {
        bh.consume(combiner.combine(HeavyAdder::compute));

    }

    @Benchmark
    @Threads(8)
    public void combiner_8threads( Blackhole bh) {
        bh.consume(combiner.combine(HeavyAdder::compute));

    }


    static class IntAdder{
        int a = 0;

        int incrementAndGet(){
            return ++a;
        }
    }

    static class HeavyAdder {
        private final int tokens;
        long a;

        public HeavyAdder(int tokens) {
            this.tokens = tokens;
        }

        double compute() {
            Blackhole.consumeCPU(tokens);
            return ++a;
        };
    }
}
