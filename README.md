# Transactional Maps
## Mvcc Transactional Map
This transactional map uses MVCC to allow readers and writers to operate without blocking each other. Readers snapshot the map state at transaction start and always see a consistent view, writes are versioned per key and old versions are pruned once no active transaction can reach them. This map promises SNAPSHOT isolation guarantees.

# Benchmarks
## Zipfian Bench
```java
Benchmark                                              (versionChainType)   Mode  Cnt         Score        Error  Units
ZipfianBenchmark.highSkew_readHeavy_1thread                         queue  thrpt   15   2949543.225 ± 605058.541  ops/s
ZipfianBenchmark.highSkew_readHeavy_1thread:aborts                  queue  thrpt   15           ≈ 0                   #
ZipfianBenchmark.highSkew_readHeavy_1thread:commits                 queue  thrpt   15  44366039.000                   #
ZipfianBenchmark.highSkew_readHeavy_1thread                           nav  thrpt   15   1502361.649 ± 181065.109  ops/s
ZipfianBenchmark.highSkew_readHeavy_1thread:aborts                    nav  thrpt   15           ≈ 0                   #
ZipfianBenchmark.highSkew_readHeavy_1thread:commits                   nav  thrpt   15  22555685.000                   #
ZipfianBenchmark.highSkew_readHeavy_2threads                        queue  thrpt   15   3450096.540 ± 247576.046  ops/s
ZipfianBenchmark.highSkew_readHeavy_2threads:aborts                 queue  thrpt   15     69704.000                   #
ZipfianBenchmark.highSkew_readHeavy_2threads:commits                queue  thrpt   15  51722355.000                   #
ZipfianBenchmark.highSkew_readHeavy_2threads                          nav  thrpt   15   2027477.224 ± 105458.277  ops/s
ZipfianBenchmark.highSkew_readHeavy_2threads:aborts                   nav  thrpt   15     54365.000                   #
ZipfianBenchmark.highSkew_readHeavy_2threads:commits                  nav  thrpt   15  30436189.000                   #
ZipfianBenchmark.highSkew_readHeavy_4threads                        queue  thrpt   15   4523013.425 ± 159685.613  ops/s
ZipfianBenchmark.highSkew_readHeavy_4threads:aborts                 queue  thrpt   15    236593.000                   #
ZipfianBenchmark.highSkew_readHeavy_4threads:commits                queue  thrpt   15  67689178.000                   #
ZipfianBenchmark.highSkew_readHeavy_4threads                          nav  thrpt   15   2760282.492 ± 158198.932  ops/s
ZipfianBenchmark.highSkew_readHeavy_4threads:aborts                   nav  thrpt   15    190749.000                   #
ZipfianBenchmark.highSkew_readHeavy_4threads:commits                  nav  thrpt   15  41264618.000                   #
ZipfianBenchmark.highSkew_readHeavy_8threads                        queue  thrpt   15   5543931.259 ± 605323.484  ops/s
ZipfianBenchmark.highSkew_readHeavy_8threads:aborts                 queue  thrpt   15    661132.000                   #
ZipfianBenchmark.highSkew_readHeavy_8threads:commits                queue  thrpt   15  83774318.000                   #
ZipfianBenchmark.highSkew_readHeavy_8threads                          nav  thrpt   15   3198280.023 ± 316016.939  ops/s
ZipfianBenchmark.highSkew_readHeavy_8threads:aborts                   nav  thrpt   15    538522.000                   #
ZipfianBenchmark.highSkew_readHeavy_8threads:commits                  nav  thrpt   15  48805059.000                   #
ZipfianBenchmark.highSkew_writeHeavy_1thread                        queue  thrpt   15   2387028.597 ± 571382.835  ops/s
ZipfianBenchmark.highSkew_writeHeavy_1thread:aborts                 queue  thrpt   15           ≈ 0                   #
ZipfianBenchmark.highSkew_writeHeavy_1thread:commits                queue  thrpt   15  35842655.000                   #
ZipfianBenchmark.highSkew_writeHeavy_1thread                          nav  thrpt   15   1082192.894 ±  97205.144  ops/s
ZipfianBenchmark.highSkew_writeHeavy_1thread:aborts                   nav  thrpt   15           ≈ 0                   #
ZipfianBenchmark.highSkew_writeHeavy_1thread:commits                  nav  thrpt   15  16410745.000                   #
ZipfianBenchmark.highSkew_writeHeavy_2threads                       queue  thrpt   15   3156547.488 ± 238160.889  ops/s
ZipfianBenchmark.highSkew_writeHeavy_2threads:aborts                queue  thrpt   15    373998.000                   #
ZipfianBenchmark.highSkew_writeHeavy_2threads:commits               queue  thrpt   15  47082464.000                   #
ZipfianBenchmark.highSkew_writeHeavy_2threads                         nav  thrpt   15   1394444.818 ± 145291.222  ops/s
ZipfianBenchmark.highSkew_writeHeavy_2threads:aborts                  nav  thrpt   15    266347.000                   #
ZipfianBenchmark.highSkew_writeHeavy_2threads:commits                 nav  thrpt   15  21149641.000                   #
ZipfianBenchmark.highSkew_writeHeavy_4threads                       queue  thrpt   15   3818498.807 ± 153931.852  ops/s
ZipfianBenchmark.highSkew_writeHeavy_4threads:aborts                queue  thrpt   15   1387376.000                   #
ZipfianBenchmark.highSkew_writeHeavy_4threads:commits               queue  thrpt   15  56177520.000                   #
ZipfianBenchmark.highSkew_writeHeavy_4threads                         nav  thrpt   15   1785457.350 ± 200900.647  ops/s
ZipfianBenchmark.highSkew_writeHeavy_4threads:aborts                  nav  thrpt   15    765630.000                   #
ZipfianBenchmark.highSkew_writeHeavy_4threads:commits                 nav  thrpt   15  26150199.000                   #
ZipfianBenchmark.highSkew_writeHeavy_8threads                       queue  thrpt   15   3802122.606 ± 477946.109  ops/s
ZipfianBenchmark.highSkew_writeHeavy_8threads:aborts                queue  thrpt   15   3091554.000                   #
ZipfianBenchmark.highSkew_writeHeavy_8threads:commits               queue  thrpt   15  56717661.000                   #
ZipfianBenchmark.highSkew_writeHeavy_8threads                         nav  thrpt   15   1887758.475 ± 288140.811  ops/s
ZipfianBenchmark.highSkew_writeHeavy_8threads:aborts                  nav  thrpt   15   1841991.000                   #
ZipfianBenchmark.highSkew_writeHeavy_8threads:commits                 nav  thrpt   15  27973124.000                   #
ZipfianBenchmark.lowSkew_readHeavy_1thread                          queue  thrpt   15   3415873.599 ± 196327.765  ops/s
ZipfianBenchmark.lowSkew_readHeavy_1thread:aborts                   queue  thrpt   15           ≈ 0                   #
ZipfianBenchmark.lowSkew_readHeavy_1thread:commits                  queue  thrpt   15  51286938.000                   #
ZipfianBenchmark.lowSkew_readHeavy_1thread                            nav  thrpt   15   1365969.251 ±  72495.450  ops/s
ZipfianBenchmark.lowSkew_readHeavy_1thread:aborts                     nav  thrpt   15           ≈ 0                   #
ZipfianBenchmark.lowSkew_readHeavy_1thread:commits                    nav  thrpt   15  20513068.000                   #
ZipfianBenchmark.lowSkew_readHeavy_2threads                         queue  thrpt   15   3725355.916 ± 230330.986  ops/s
ZipfianBenchmark.lowSkew_readHeavy_2threads:aborts                  queue  thrpt   15      9229.000                   #
ZipfianBenchmark.lowSkew_readHeavy_2threads:commits                 queue  thrpt   15  55938308.000                   #
ZipfianBenchmark.lowSkew_readHeavy_2threads                           nav  thrpt   15   1884607.406 ± 163528.450  ops/s
ZipfianBenchmark.lowSkew_readHeavy_2threads:aborts                    nav  thrpt   15     58699.000                   #
ZipfianBenchmark.lowSkew_readHeavy_2threads:commits                   nav  thrpt   15  28293660.000                   #
ZipfianBenchmark.lowSkew_readHeavy_4threads                         queue  thrpt   15   4671228.113 ± 232495.224  ops/s
ZipfianBenchmark.lowSkew_readHeavy_4threads:aborts                  queue  thrpt   15     34337.000                   #
ZipfianBenchmark.lowSkew_readHeavy_4threads:commits                 queue  thrpt   15  70192449.000                   #
ZipfianBenchmark.lowSkew_readHeavy_4threads                           nav  thrpt   15   2670070.306 ± 217270.577  ops/s
ZipfianBenchmark.lowSkew_readHeavy_4threads:aborts                    nav  thrpt   15     29936.000                   #
ZipfianBenchmark.lowSkew_readHeavy_4threads:commits                   nav  thrpt   15  40201040.000                   #
ZipfianBenchmark.lowSkew_readHeavy_8threads                         queue  thrpt   15   5976820.187 ± 335476.830  ops/s
ZipfianBenchmark.lowSkew_readHeavy_8threads:aborts                  queue  thrpt   15    114780.000                   #
ZipfianBenchmark.lowSkew_readHeavy_8threads:commits                 queue  thrpt   15  91088264.000                   #
ZipfianBenchmark.lowSkew_readHeavy_8threads                           nav  thrpt   15   3510067.800 ± 270407.070  ops/s
ZipfianBenchmark.lowSkew_readHeavy_8threads:aborts                    nav  thrpt   15    109459.000                   #
ZipfianBenchmark.lowSkew_readHeavy_8threads:commits                   nav  thrpt   15  53818747.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_1thread                         queue  thrpt   15   2637877.439 ± 117063.819  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_1thread:aborts                  queue  thrpt   15           ≈ 0                   #
ZipfianBenchmark.lowSkew_writeHeavy_1thread:commits                 queue  thrpt   15  39603389.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_1thread                           nav  thrpt   15    974789.590 ±  58512.151  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_1thread:aborts                    nav  thrpt   15           ≈ 0                   #
ZipfianBenchmark.lowSkew_writeHeavy_1thread:commits                   nav  thrpt   15  14741885.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_2threads                        queue  thrpt   15   3052438.175 ± 229338.826  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_2threads:aborts                 queue  thrpt   15     49874.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_2threads:commits                queue  thrpt   15  45828520.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_2threads                          nav  thrpt   15   1376527.766 ± 119123.444  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_2threads:aborts                   nav  thrpt   15     26142.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_2threads:commits                  nav  thrpt   15  20844385.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_4threads                        queue  thrpt   15   3753860.923 ± 146870.699  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_4threads:aborts                 queue  thrpt   15    218034.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_4threads:commits                queue  thrpt   15  56177050.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_4threads                          nav  thrpt   15   1816686.176 ± 283857.758  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_4threads:aborts                   nav  thrpt   15    153301.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_4threads:commits                  nav  thrpt   15  27613979.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_8threads                        queue  thrpt   15   4052740.379 ± 427334.718  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_8threads:aborts                 queue  thrpt   15    563609.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_8threads:commits                queue  thrpt   15  63042618.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_8threads                          nav  thrpt   15   1868367.977 ± 247523.476  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_8threads:aborts                   nav  thrpt   15    383145.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_8threads:commits                  nav  thrpt   15  28943769.000                   #

```

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