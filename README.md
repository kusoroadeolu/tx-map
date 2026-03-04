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
TxMapCombinerBenchmark.opsPerTx_1                array  thrpt   10  4737779.240 ±  537221.574  ops/s
TxMapCombinerBenchmark.opsPerTx_1              unbound  thrpt   10  5723643.097 ± 416520.865  ops/s
TxMapCombinerBenchmark.opsPerTx_1                  nc  thrpt   10  5179001.530 ±  135290.294  ops/s
TxMapCombinerBenchmark.opsPerTx_1                 sync  thrpt   10  6217976.352 ±  231458.470  ops/s
TxMapCombinerBenchmark.opsPerTx_10               array  thrpt   10  1416871.309 ±   91271.762  ops/s
TxMapCombinerBenchmark.opsPerTx_10             unbound  thrpt   10  1404021.317 ±  62894.514  ops/s
TxMapCombinerBenchmark.opsPerTx_10                 nc  thrpt   10  1453856.993 ±   31773.948  ops/s
TxMapCombinerBenchmark.opsPerTx_10                sync  thrpt   10  1190591.304 ±   27565.546  ops/s
TxMapCombinerBenchmark.opsPerTx_3                array  thrpt   10  3278679.378 ±  177817.201  ops/s
TxMapCombinerBenchmark.opsPerTx_3              unbound  thrpt   10  3555424.796 ±  122397.442  ops/s
TxMapCombinerBenchmark.opsPerTx_3                  nc  thrpt   10  3304542.593 ±  299599.886  ops/s
TxMapCombinerBenchmark.opsPerTx_3                 sync  thrpt   10  3154468.462 ±  544692.343  ops/s
TxMapCombinerBenchmark.opsPerTx_5                array  thrpt   10  2664212.866 ±   70002.321  ops/s
TxMapCombinerBenchmark.opsPerTx_5              unbound  thrpt   10  2693999.677 ± 113875.330  ops/s
TxMapCombinerBenchmark.opsPerTx_5                  nc  thrpt   10  2588520.914 ±  459886.853  ops/s
TxMapCombinerBenchmark.opsPerTx_5                 sync  thrpt   10  2296770.481 ±  257240.572  ops/s

TxMapCombinerBenchmark.threadScaling_1           array  thrpt   10  6929699.117 ±  307262.672  ops/s
TxMapCombinerBenchmark.threadScaling_1         unbound  thrpt   10  8511146.370 ± 723544.984  ops/s
TxMapCombinerBenchmark.threadScaling_1             nc  thrpt   10  6915508.317 ± 2958481.297  ops/s
TxMapCombinerBenchmark.threadScaling_1            sync  thrpt   10  6382654.957 ± 1991089.346  ops/s
TxMapCombinerBenchmark.threadScaling_2           array  thrpt   10  5058250.884 ±  544102.028  ops/s
TxMapCombinerBenchmark.threadScaling_2         unbound  thrpt   10  7383069.336 ± 464884.128  ops/s
TxMapCombinerBenchmark.threadScaling_2             nc  thrpt   10  4813470.532 ±  894455.392  ops/s
TxMapCombinerBenchmark.threadScaling_2            sync  thrpt   10  4809004.123 ±  280161.792  ops/s
TxMapCombinerBenchmark.threadScaling_4           array  thrpt   10  4409963.080 ±  157230.285  ops/s
TxMapCombinerBenchmark.threadScaling_4         unbound  thrpt   10  6187794.985 ± 464350.263  ops/s
TxMapCombinerBenchmark.threadScaling_4             nc  thrpt   10  4680105.067 ±  112318.133  ops/s
TxMapCombinerBenchmark.threadScaling_4            sync  thrpt   10  5553577.128 ±  712185.610  ops/s
TxMapCombinerBenchmark.threadScaling_8           array  thrpt   10  3888263.499 ±  584979.658  ops/s
TxMapCombinerBenchmark.threadScaling_8         unbound  thrpt   10  4329993.012 ± 140068.645  ops/s
TxMapCombinerBenchmark.threadScaling_8             nc  thrpt   10  4149041.185 ±   47802.527  ops/s
TxMapCombinerBenchmark.threadScaling_8            sync  thrpt   10  5722148.204 ±  306634.232  ops/s



## Segmented, one combiner per key, one for size
### Ungrouped by key i.e. we iterate the whole list and on each iteration, there's a combine call
Benchmark                                    (combinerType)   Mode  Cnt        Score        Error  Units
SegmentedCombinedTxMapBench.opsPerTx_1                array  thrpt   10  2886211.696 ± 220157.702  ops/s
SegmentedCombinedTxMapBench.opsPerTx_1              unbound  thrpt   10  3477544.988 ± 304663.005  ops/s
SegmentedCombinedTxMapBench.opsPerTx_1                  nc  thrpt   10  2823710.983 ±  71388.143  ops/s
SegmentedCombinedTxMapBench.opsPerTx_1                 sync  thrpt   10  3939241.577 ±  69923.137  ops/s
SegmentedCombinedTxMapBench.opsPerTx_10               array  thrpt   10   485105.950 ±   8011.112  ops/s
SegmentedCombinedTxMapBench.opsPerTx_10             unbound  thrpt   10   581470.243 ±   9196.640  ops/s
SegmentedCombinedTxMapBench.opsPerTx_10                 nc  thrpt   10   452239.287 ±  15626.242  ops/s
SegmentedCombinedTxMapBench.opsPerTx_10                sync  thrpt   10   660291.487 ±  17522.662  ops/s
SegmentedCombinedTxMapBench.opsPerTx_3                array  thrpt   10  1419134.670 ±  63877.100  ops/s
SegmentedCombinedTxMapBench.opsPerTx_3              unbound  thrpt   10  1676008.876 ±  91543.450  ops/s
SegmentedCombinedTxMapBench.opsPerTx_3                  nc  thrpt   10  1321787.918 ±  96482.231  ops/s
SegmentedCombinedTxMapBench.opsPerTx_3                 sync  thrpt   10  1852832.352 ± 117459.309  ops/s
SegmentedCombinedTxMapBench.opsPerTx_5                array  thrpt   10   932979.381 ±  38953.993  ops/s
SegmentedCombinedTxMapBench.opsPerTx_5              unbound  thrpt   10  1125330.855 ±  19420.244  ops/s
SegmentedCombinedTxMapBench.opsPerTx_5                  nc  thrpt   10   869678.333 ±  40776.541  ops/s
SegmentedCombinedTxMapBench.opsPerTx_5                 sync  thrpt   10  1238611.577 ±  28694.246  ops/s

SegmentedCombinedTxMapBench.threadScaling_1           array  thrpt   10  4070127.636 ± 358300.948  ops/s
SegmentedCombinedTxMapBench.threadScaling_1         unbound  thrpt   10  5356052.636 ± 501416.693  ops/s
SegmentedCombinedTxMapBench.threadScaling_1             nc  thrpt   10  4826272.962 ± 193816.677  ops/s
SegmentedCombinedTxMapBench.threadScaling_1            sync  thrpt   10  7497816.587 ± 601580.070  ops/s
SegmentedCombinedTxMapBench.threadScaling_2           array  thrpt   10  3550716.939 ±  95172.894  ops/s
SegmentedCombinedTxMapBench.threadScaling_2         unbound  thrpt   10  4638429.386 ± 262088.651  ops/s
SegmentedCombinedTxMapBench.threadScaling_2             nc  thrpt   10  3859925.304 ± 139552.194  ops/s
SegmentedCombinedTxMapBench.threadScaling_2            sync  thrpt   10  5149117.472 ± 263505.181  ops/s
SegmentedCombinedTxMapBench.threadScaling_4           array  thrpt   10  2957789.984 ± 178641.037  ops/s
SegmentedCombinedTxMapBench.threadScaling_4         unbound  thrpt   10  3779271.022 ±  75191.693  ops/s
SegmentedCombinedTxMapBench.threadScaling_4             nc  thrpt   10  2960248.943 ± 101171.928  ops/s
SegmentedCombinedTxMapBench.threadScaling_4            sync  thrpt   10  4164416.951 ± 204767.223  ops/s
SegmentedCombinedTxMapBench.threadScaling_8           array  thrpt   10  2752415.804 ± 129779.619  ops/s
SegmentedCombinedTxMapBench.threadScaling_8         unbound  thrpt   10  2922649.999 ±  79227.877  ops/s
SegmentedCombinedTxMapBench.threadScaling_8             nc  thrpt   10  2508414.624 ± 118512.164  ops/s
SegmentedCombinedTxMapBench.threadScaling_8            sync  thrpt   10  4205848.153 ± 267646.789  ops/s

## Grouped by key i.e. we combine operations per key 
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
