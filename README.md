# Flat combined transactional map
This transactional map provides both serializable isolation and full atomicity guarantees for transactions. Transactions are fully serialized meaning only one transaction can execute at a time, however, each transaction might or might not be run by their own thread, due to the nature of flat combining.
The main goal of this transactional map, is to integrate transactional and flat combining techniques into a map. Transactions in this map are lazy, meaning they aren't processed until commit time. This map provides two combiners to test with
- **Unbound combiner:** This combiner allows an unfixed amount of threads to concurrently access it, however to prevent nodes from growing, a node cleanup is performed infrequently by the combiner after the combiner has been executed a certain amount of times. It is also lock based
- **Node Cycling combiner:** This combiner allows an unfixed amount of threads to concurrently access it, to prevent nodes from growing indefinitely, this combiner implements node cycling and unlinking at combining time, rather than scanning at a fixed threshold .
You can find the standalone benchmarks for both combiners [here](txmap-benchmarks/combiner-bmh/combiner-bmh.json)

## Benchmarks for the transactional map using different combiners

## Raw combiner benchmarks, with varying idle strategies
- Black hole consume cpu(with 10 tokens)

Benchmark                            (combinerType)  (idleStrat)   Mode  Cnt         Score         Error  Units
CombinerBenchmark.combiner_4threads           array         spin  thrpt   10   4940902.888 ±  364426.986  ops/s
CombinerBenchmark.combiner_4threads           array         park  thrpt   10   3003354.235 ±  980447.156  ops/s
CombinerBenchmark.combiner_4threads           array        yield  thrpt   10   4134034.114 ± 1286555.947  ops/s
CombinerBenchmark.combiner_4threads           array    spin-loop  thrpt   10   3051151.524 ±  713353.553  ops/s
CombinerBenchmark.combiner_4threads         unbound         spin  thrpt   10   5341276.898 ± 1006531.422  ops/s
CombinerBenchmark.combiner_4threads         unbound         park  thrpt   10  10424327.575 ± 1069039.612  ops/s
CombinerBenchmark.combiner_4threads         unbound        yield  thrpt   10   5593392.621 ± 1430692.557  ops/s
CombinerBenchmark.combiner_4threads         unbound    spin-loop  thrpt   10  11553378.426 ±  632749.613  ops/s
CombinerBenchmark.combiner_4threads             nc         spin  thrpt   10   4766121.908 ±  246506.884  ops/s
CombinerBenchmark.combiner_4threads             nc         park  thrpt   10   8609374.243 ±  274982.970  ops/s
CombinerBenchmark.combiner_4threads             nc        yield  thrpt   10   4643067.639 ±  291553.856  ops/s
CombinerBenchmark.combiner_4threads             nc    spin-loop  thrpt   10   8916522.915 ±  382107.834  ops/s

### Baseline
Benchmark                                     Mode  Cnt         Score        Error  Units
SynchronizedCombinerBench.combiner_4threads  thrpt   10  17515595.325 ± 910069.618  ops/s

## More Threads/Higher token count, comparing idle strategies
Benchmark                            (combinerType)  (idleStrat)  (tokens)   Mode  Cnt        Score        Error  Units
CombinerBenchmark.combiner_4threads           array         spin       500  thrpt   10   628728.508 ±  29221.238  ops/s
CombinerBenchmark.combiner_4threads           array         park       500  thrpt   10   904079.672 ±  38689.143  ops/s
CombinerBenchmark.combiner_4threads           array        yield       500  thrpt   10   614835.002 ±  27355.834  ops/s
CombinerBenchmark.combiner_4threads           array    spin-loop       500  thrpt   10   687663.741 ±  41021.797  ops/s
CombinerBenchmark.combiner_4threads         unbound         spin       500  thrpt   10   611607.834 ±  25861.686  ops/s
CombinerBenchmark.combiner_4threads         unbound         park       500  thrpt   10  1248335.698 ± 652469.407  ops/s
CombinerBenchmark.combiner_4threads         unbound        yield       500  thrpt   10   649049.601 ±  49499.473  ops/s
CombinerBenchmark.combiner_4threads         unbound    spin-loop       500  thrpt   10   787298.705 ±  42212.581  ops/s
CombinerBenchmark.combiner_4threads             nc         spin       500  thrpt   10   608923.375 ±  62442.940  ops/s
CombinerBenchmark.combiner_4threads             nc         park       500  thrpt   10  1017153.565 ±  73932.567  ops/s
CombinerBenchmark.combiner_4threads             nc        yield       500  thrpt   10   590685.557 ±  52489.373  ops/s
CombinerBenchmark.combiner_4threads             nc    spin-loop       500  thrpt   10   752594.452 ±  85483.183  ops/s

CombinerBenchmark.combiner_8threads           array         spin       500  thrpt   10   500721.652 ±  21420.571  ops/s
CombinerBenchmark.combiner_8threads           array         park       500  thrpt   10   768761.789 ± 166924.699  ops/s
CombinerBenchmark.combiner_8threads           array        yield       500  thrpt   10   462076.420 ±  16345.871  ops/s
CombinerBenchmark.combiner_8threads           array    spin-loop       500  thrpt   10   677144.723 ±  17975.015  ops/s
CombinerBenchmark.combiner_8threads         unbound         spin       500  thrpt   10   507005.600 ±  23656.677  ops/s
CombinerBenchmark.combiner_8threads         unbound         park       500  thrpt   10  1032196.219 ± 483196.423  ops/s
CombinerBenchmark.combiner_8threads         unbound        yield       500  thrpt   10   452305.365 ±  16989.337  ops/s
CombinerBenchmark.combiner_8threads         unbound    spin-loop       500  thrpt   10   708503.064 ±  49134.413  ops/s
CombinerBenchmark.combiner_8threads             nc         spin       500  thrpt   10   485206.218 ±  44766.243  ops/s
CombinerBenchmark.combiner_8threads             nc         park       500  thrpt   10   981706.433 ± 176670.040  ops/s
CombinerBenchmark.combiner_8threads             nc        yield       500  thrpt   10   461104.951 ±  22820.683  ops/s
CombinerBenchmark.combiner_8threads             nc    spin-loop       500  thrpt   10   715079.096 ±  10394.087  ops/s

#### Benchmarks for unbound combiner(optimized)
Benchmark                            (combinerType)  (idleStrat)  (tokens)   Mode  Cnt        Score       Error  Units
CombinerBenchmark.combiner_4threads         unbound         spin       500  thrpt   10   684851.733 ± 22794.756  ops/s
CombinerBenchmark.combiner_4threads         unbound         park       500  thrpt   10  1049309.698 ± 70593.826  ops/s
CombinerBenchmark.combiner_4threads         unbound        yield       500  thrpt   10   701403.228 ± 29511.465  ops/s
CombinerBenchmark.combiner_4threads         unbound    spin-loop       500  thrpt   10   848802.908 ± 22291.390  ops/s
CombinerBenchmark.combiner_8threads         unbound         spin       500  thrpt   10   544013.641 ± 43040.674  ops/s
CombinerBenchmark.combiner_8threads         unbound         park       500  thrpt   10  1056610.886 ± 84884.360  ops/s
CombinerBenchmark.combiner_8threads         unbound        yield       500  thrpt   10   489565.013 ± 11171.170  ops/s
CombinerBenchmark.combiner_8threads         unbound    spin-loop       500  thrpt   10   752565.126 ± 41629.693  ops/s

**NOTE:** I basically reduced the frequency in which combiners now scan for aged nodes i.e. before i used a simple (if combinerPass > threshold ... scan), now I do (if combinerPass % threshold == 0 ... scan). This small change actually made huge differences to the thrpt and variance of the unbound combiner under contention.
At 4 and 8 threads, the thrpt of each idle strategy(except park), dramatically increased by ~11% across all strategies while their error margins also became tighter. For park, while the thrpt did decrease by around ~20% at 4 threads, the error margins reduce by ~89% across both threads. Before after a certain threshold, a combiners always had to rescan the queue for old nodes, now, combiners don't need to rescan loop after that threshold everytime, they just scan at multiples of that threshold, leading to less work and pointer chasing per scan for each combiner.



Benchmark                                    (tokens)   Mode  Cnt       Score       Error  Units
SynchronizedCombinerBench.combiner_4threads       500  thrpt   10  871699.325 ± 22210.857  ops/s
SynchronizedCombinerBench.combiner_8threads       500  thrpt   10  844974.290 ± 27657.471  ops/s


### Serialized one combiner for all transactions
These benchmarks measure how throughput varies as the number of operations per transaction increases on 4 threads(cause this is an 8 core machine) and how throughput varies as number of threads increases with a cap of one operation per transaction
Txmap bench
Benchmark                               (combinerType)   Mode  Cnt        Score         Error  Units
SerializedCombinedTxMapBench.opsPerTx_1                array  thrpt   10  2035607.136 ±  485532.746  ops/s
SerializedCombinedTxMapBench.opsPerTx_1              unbound  thrpt   10  4026594.460 ±  651390.167  ops/s
SerializedCombinedTxMapBench.opsPerTx_1                   nc  thrpt   10  3318562.772 ±  840909.192  ops/s
SerializedCombinedTxMapBench.opsPerTx_1                 sync  thrpt   10  3149912.328 ± 1294266.733  ops/s
SerializedCombinedTxMapBench.opsPerTx_10               array  thrpt   10   843802.555 ±   99321.214  ops/s
SerializedCombinedTxMapBench.opsPerTx_10             unbound  thrpt   10  1137655.936 ±  150530.755  ops/s
SerializedCombinedTxMapBench.opsPerTx_10                  nc  thrpt   10  1038659.943 ±  281312.129  ops/s
SerializedCombinedTxMapBench.opsPerTx_10                sync  thrpt   10   961801.348 ±   44583.734  ops/s
SerializedCombinedTxMapBench.opsPerTx_3                array  thrpt   10  1808437.566 ±   71355.954  ops/s
SerializedCombinedTxMapBench.opsPerTx_3              unbound  thrpt   10  3026230.655 ±   22335.965  ops/s
SerializedCombinedTxMapBench.opsPerTx_3                   nc  thrpt   10  2708339.856 ±   40824.635  ops/s
SerializedCombinedTxMapBench.opsPerTx_3                 sync  thrpt   10  2642395.916 ±  206248.205  ops/s
SerializedCombinedTxMapBench.opsPerTx_5                array  thrpt   10  1563211.977 ±  118275.273  ops/s
SerializedCombinedTxMapBench.opsPerTx_5              unbound  thrpt   10  2362182.572 ±  113534.109  ops/s
SerializedCombinedTxMapBench.opsPerTx_5                   nc  thrpt   10  2041684.457 ±   87893.009  ops/s
SerializedCombinedTxMapBench.opsPerTx_5                 sync  thrpt   10  1930465.197 ±   85272.367  ops/s

SerializedCombinedTxMapBench.threadScaling_1           array  thrpt   10  3367473.606 ±  276534.003  ops/s
SerializedCombinedTxMapBench.threadScaling_1         unbound  thrpt   10  7601196.920 ±  632005.414  ops/s
SerializedCombinedTxMapBench.threadScaling_1              nc  thrpt   10  6610352.518 ± 1464180.449  ops/s
SerializedCombinedTxMapBench.threadScaling_1            sync  thrpt   10  9730231.268 ± 1017150.725  ops/s
SerializedCombinedTxMapBench.threadScaling_2           array  thrpt   10  2899527.677 ±  276899.336  ops/s
SerializedCombinedTxMapBench.threadScaling_2         unbound  thrpt   10  6495323.083 ±  387572.424  ops/s
SerializedCombinedTxMapBench.threadScaling_2              nc  thrpt   10  5713073.861 ±  252924.103  ops/s
SerializedCombinedTxMapBench.threadScaling_2            sync  thrpt   10  4157548.356 ±  912528.821  ops/s
SerializedCombinedTxMapBench.threadScaling_4           array  thrpt   10  2517637.095 ±   53466.073  ops/s
SerializedCombinedTxMapBench.threadScaling_4         unbound  thrpt   10  5250752.253 ±   56829.431  ops/s
SerializedCombinedTxMapBench.threadScaling_4              nc  thrpt   10  4475130.618 ±  325295.920  ops/s
SerializedCombinedTxMapBench.threadScaling_4            sync  thrpt   10  5458893.445 ±  247393.008  ops/s
SerializedCombinedTxMapBench.threadScaling_8           array  thrpt   10  2112678.138 ±   78064.767  ops/s
SerializedCombinedTxMapBench.threadScaling_8         unbound  thrpt   10  3949911.789 ±  153363.448  ops/s
SerializedCombinedTxMapBench.threadScaling_8              nc  thrpt   10  3574091.422 ±  159766.742  ops/s
SerializedCombinedTxMapBench.threadScaling_8            sync  thrpt   10  5321637.906 ±  740854.237  ops/s



## Segmented, one combiner per key, one for size
Benchmark                                    (combinerType)   Mode  Cnt        Score        Error  Units
SegmentedCombinedTxMapBench.opsPerTx_1                array  thrpt   10  2232550.962 ±  45866.551  ops/s
SegmentedCombinedTxMapBench.opsPerTx_1              unbound  thrpt   10  2606415.859 ± 104647.493  ops/s
SegmentedCombinedTxMapBench.opsPerTx_1                  nc  thrpt   10  2244868.597 ± 146409.633  ops/s
SegmentedCombinedTxMapBench.opsPerTx_1                 sync  thrpt   10  2790837.348 ±  81607.535  ops/s
SegmentedCombinedTxMapBench.opsPerTx_10               array  thrpt   10   723517.672 ±   8533.269  ops/s
SegmentedCombinedTxMapBench.opsPerTx_10             unbound  thrpt   10   761726.945 ±  55332.961  ops/s
SegmentedCombinedTxMapBench.opsPerTx_10                 nc  thrpt   10   668119.680 ±  56287.586  ops/s
SegmentedCombinedTxMapBench.opsPerTx_10                sync  thrpt   10   806814.216 ±  15438.751  ops/s
SegmentedCombinedTxMapBench.opsPerTx_3                array  thrpt   10  1224098.990 ±  29246.349  ops/s
SegmentedCombinedTxMapBench.opsPerTx_3              unbound  thrpt   10  1299269.381 ± 101783.144  ops/s
SegmentedCombinedTxMapBench.opsPerTx_3                  nc  thrpt   10  1158167.749 ±  40892.776  ops/s
SegmentedCombinedTxMapBench.opsPerTx_3                 sync  thrpt   10  1435920.533 ± 299863.442  ops/s
SegmentedCombinedTxMapBench.opsPerTx_5                array  thrpt   10   954758.422 ±  72324.657  ops/s
SegmentedCombinedTxMapBench.opsPerTx_5              unbound  thrpt   10   954221.969 ± 118161.235  ops/s
SegmentedCombinedTxMapBench.opsPerTx_5                  nc  thrpt   10   839950.360 ±  95603.769  ops/s
SegmentedCombinedTxMapBench.opsPerTx_5                 sync  thrpt   10  1173392.139 ±  47904.276  ops/s

SegmentedCombinedTxMapBench.threadScaling_1           array  thrpt   10  3116543.842 ± 216750.851  ops/s
SegmentedCombinedTxMapBench.threadScaling_1         unbound  thrpt   10  3114461.083 ± 499945.964  ops/s
SegmentedCombinedTxMapBench.threadScaling_1             nc  thrpt   10  3047231.189 ± 545465.463  ops/s
SegmentedCombinedTxMapBench.threadScaling_1            sync  thrpt   10  4739156.517 ± 143798.249  ops/s
SegmentedCombinedTxMapBench.threadScaling_2           array  thrpt   10  2514623.877 ± 216162.672  ops/s
SegmentedCombinedTxMapBench.threadScaling_2         unbound  thrpt   10  3260953.219 ±  21435.516  ops/s
SegmentedCombinedTxMapBench.threadScaling_2             nc  thrpt   10  2852613.306 ±  73383.741  ops/s
SegmentedCombinedTxMapBench.threadScaling_2            sync  thrpt   10  3748824.299 ± 193752.485  ops/s
SegmentedCombinedTxMapBench.threadScaling_4           array  thrpt   10  2309965.194 ±  67208.068  ops/s
SegmentedCombinedTxMapBench.threadScaling_4         unbound  thrpt   10  2765969.556 ±  67195.551  ops/s
SegmentedCombinedTxMapBench.threadScaling_4             nc  thrpt   10  2376266.225 ±  70225.792  ops/s
SegmentedCombinedTxMapBench.threadScaling_4            sync  thrpt   10  2665269.678 ± 162858.466  ops/s
SegmentedCombinedTxMapBench.threadScaling_8           array  thrpt   10  2249172.290 ±  69812.162  ops/s
SegmentedCombinedTxMapBench.threadScaling_8         unbound  thrpt   10  2373319.698 ± 105978.540  ops/s
SegmentedCombinedTxMapBench.threadScaling_8             nc  thrpt   10  2100690.143 ± 200311.378  ops/s
SegmentedCombinedTxMapBench.threadScaling_8            sync  thrpt   10  2772993.317 ± 170612.196  ops/s
