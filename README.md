# Transactional Maps
## Optimistic Transactional Map
This transactional map's semantics require readers eagerly stating their intent by acquiring read locks for their semantics at scheduled time(before commits). Writes however are serialized through one lock per key and lazily state their intent
This map promises READ COMMITTED Isolation guarantees, this guarantee applies to both Pessimistic and Combiner Maps

# Benchmarks
## Contention Benchmarks
Benchmark                                 Mode  Cnt        Score        Error  Units
  ContentionBenchmark.balanced_1thread     thrpt   10   867721.961 ± 315544.095  ops/s
  ContentionBenchmark.balanced_2threads    thrpt   10  1022766.460 ± 196795.874  ops/s
  ContentionBenchmark.balanced_4threads    thrpt   10   650645.544 ± 203857.492  ops/s
  ContentionBenchmark.balanced_8threads    thrpt   10   498178.960 ± 187042.209  ops/s
  ContentionBenchmark.readHeavy_1thread    thrpt   10  1266642.451 ± 304989.799  ops/s
  ContentionBenchmark.readHeavy_2threads   thrpt   10  1618470.600 ± 139661.272  ops/s
  ContentionBenchmark.readHeavy_4threads   thrpt   10   999253.579 ±  28831.378  ops/s
  ContentionBenchmark.readHeavy_8threads   thrpt   10   701199.276 ± 236488.421  ops/s
  ContentionBenchmark.writeHeavy_1thread   thrpt   10   883039.762 ±  91229.210  ops/s
  ContentionBenchmark.writeHeavy_2threads  thrpt   10   969683.192 ± 403437.561  ops/s
  ContentionBenchmark.writeHeavy_4threads  thrpt   10   376637.494 ±  68776.718  ops/s
  ContentionBenchmark.writeHeavy_8threads  thrpt   10   340917.224 ±  77466.989  ops/s

## Disjoint Key Benchmarks
Benchmark                                    Mode  Cnt        Score        Error  Units
DisjointKeyBenchmark.txMap_batch_16threads  thrpt   10   884898.064 ± 149815.253  ops/s
DisjointKeyBenchmark.txMap_batch_1thread    thrpt   10   527043.350 ± 202792.689  ops/s
DisjointKeyBenchmark.txMap_batch_2threads   thrpt   10   929591.929 ±  76169.784  ops/s
DisjointKeyBenchmark.txMap_batch_4threads   thrpt   10  1211591.385 ±  56441.324  ops/s
DisjointKeyBenchmark.txMap_batch_8threads   thrpt   10  1192992.450 ±  57100.861  ops/s
DisjointKeyBenchmark.txMap_put_16threads    thrpt   10  2028808.901 ± 231063.721  ops/s
DisjointKeyBenchmark.txMap_put_1thread      thrpt   10  1106413.379 ± 127584.820  ops/s
DisjointKeyBenchmark.txMap_put_2threads     thrpt   10  1471273.398 ± 283775.306  ops/s
DisjointKeyBenchmark.txMap_put_4threads     thrpt   10  1834450.369 ± 110858.141  ops/s
DisjointKeyBenchmark.txMap_put_8threads     thrpt   10  2053988.876 ±  61456.292  ops/s

# Running the Benchmarks

## Prerequisites
- Java (version X+)
- Maven

## Steps

1. **Build the jar** from the parent pom:
```bash
   mvn clean package -U
```

2. **Run the benchmarks:**
```bash
   java -jar txmap-benchmarks/target/benchmark.jar {BenchmarkClassName(Without the Parentheses)} -rf json -rff results.json
```

## Output
Results will be saved to `results.json` in your current directory. You can open this with any JMH-compatible visualizer (e.g. [jmh.morethan.io](https://jmh.morethan.io/)).