package io.github.kusoroadeolu.txcoll.benchmarks;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class DisjointKeyBenchmarkRunner {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(DisjointKeyBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
