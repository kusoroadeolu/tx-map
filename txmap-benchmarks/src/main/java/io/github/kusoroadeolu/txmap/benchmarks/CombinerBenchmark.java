package io.github.kusoroadeolu.txmap.benchmarks;

import io.github.kusoroadeolu.txmap.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2)
public class CombinerBenchmark {

    private final static int MAX_SPINS = 256;

    private Combiner<HeavyAdder> combiner;
    private Combiner.IdleStrategy idleStrategy;

    @Param({"array", "unbound", "sem"})
    private String combinerType;

    @Param({"spin", "park", "yield", "spin-loop"})
    private String idleStrat;

    @Param({"500"})
    private int tokens;


    private int cap = 100;

    @Setup(Level.Trial)
    public void setup() {
         idleStrategy = switch (idleStrat) {
            case "spin" -> Combiner.IdleStrategy.busySpin();
            case "park" -> Combiner.IdleStrategy.park(MAX_SPINS);
            case "yield" -> Combiner.IdleStrategy.yield(MAX_SPINS);
            case "spin-loop" -> Combiner.IdleStrategy.spinLoop(MAX_SPINS);
            default -> throw new IllegalStateException("Unexpected value: " + idleStrat);
         };

        combiner = switch (combinerType) {
            case "array" -> new AtomicArrayCombiner<>(new HeavyAdder(tokens), cap);
            case "unbound" -> new UnboundCombiner<>(new HeavyAdder(tokens), cap);
            case "sem" -> new SemaphoreCombiner<>(new HeavyAdder(tokens), cap);
            default -> throw new IllegalStateException("Unexpected value: " + combinerType);
        };
    }

    @Benchmark
    @Threads(4)
    public void combiner_4threads( Blackhole bh) {
        bh.consume(combiner.combine(HeavyAdder::compute, idleStrategy));

    }

    @Benchmark
    @Threads(8)
    public void combiner_8threads( Blackhole bh) {
        bh.consume(combiner.combine(HeavyAdder::compute, idleStrategy));

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
