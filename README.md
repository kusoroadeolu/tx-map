# Flat combined transactional map
This transactional map provides both serializable isolation and full atomicity guarantees for transactions. Transactions are fully serialized meaning only one transaction can execute at a time, however, each transaction might or might not be run by their own thread, due to the nature of flat combining.
The main goal of this transactional map, is to integrate transactional and flat combining techniques into a map. Transactions in this map are lazy, meaning they aren't processed until commit time. This map provides two combiners to test with
- **Unbound combiner:** This combiner allows an unfixed amount of threads to concurrently access it, however to prevent nodes from growing, a node cleanup is performed infrequently by the combiner after the combiner has been executed a certain amount of times. It is also lock based
- **Semaphore combiner:** This combiner allows a fixed amount of threads to concurrently access it, to prevent nodes from growing, this combiner implements node reuse, however if this combiner is accessed by multiple threads greater than the fixed amount, the queue could grow exponentially large
You can find the standalone benchmarks for both combiners [here](txmap-benchmarks/combiner-bmh/combiner-bmh.json)

## Benchmarks for the transactional map using different combiners
These benchmarks measure how throughput varies as the number of operations per transaction increases on 4 threads(cause this is an 8 core machine) and how throughput varies as number of threads increases with a cap of one operation per transaction

## Unbound 
Benchmark                                Mode  Cnt        Score         Error  Units
TxMapCombinerBenchmark.opsPerTx_1       thrpt   10  6140572.974 ±  201579.050  ops/s
TxMapCombinerBenchmark.opsPerTx_10      thrpt   10  1394195.276 ±   41433.308  ops/s
TxMapCombinerBenchmark.opsPerTx_3       thrpt   10  3104740.060 ±  286448.307  ops/s
TxMapCombinerBenchmark.opsPerTx_5       thrpt   10  2454691.826 ±  164267.778  ops/s
TxMapCombinerBenchmark.threadScaling_1  thrpt   10  8483527.698 ±  686284.284  ops/s
TxMapCombinerBenchmark.threadScaling_2  thrpt   10  7152377.008 ± 1016643.888  ops/s
TxMapCombinerBenchmark.threadScaling_4  thrpt   10  5796094.413 ± 1243513.363  ops/s
TxMapCombinerBenchmark.threadScaling_8  thrpt   10  4755282.344 ± 1775135.161  ops/s

## Semaphore
Benchmark                                Mode  Cnt        Score        Error  Units
TxMapCombinerBenchmark.opsPerTx_1       thrpt   10  4977032.962 ± 727554.870  ops/s
TxMapCombinerBenchmark.opsPerTx_10      thrpt   10  1354263.023 ± 235663.368  ops/s
TxMapCombinerBenchmark.opsPerTx_3       thrpt   10  2932603.052 ± 123429.121  ops/s
TxMapCombinerBenchmark.opsPerTx_5       thrpt   10  2340429.025 ± 133832.700  ops/s
TxMapCombinerBenchmark.threadScaling_1  thrpt   10  8501070.759 ± 576502.162  ops/s
TxMapCombinerBenchmark.threadScaling_2  thrpt   10  6585958.748 ± 508671.639  ops/s
TxMapCombinerBenchmark.threadScaling_4  thrpt   10  5176171.714 ± 132479.762  ops/s
TxMapCombinerBenchmark.threadScaling_8  thrpt   10  3680776.182 ± 135464.117  ops/s

## Array
Benchmark                                Mode  Cnt        Score        Error  Units
TxMapCombinerBenchmark.opsPerTx_1        thrpt   10   3733726.925 ± 1396970.425  ops/s
TxMapCombinerBenchmark.opsPerTx_10       thrpt   10   1289441.741 ±   73968.744  ops/s
TxMapCombinerBenchmark.opsPerTx_3        thrpt   10   2795601.486 ±   99012.415  ops/s
TxMapCombinerBenchmark.opsPerTx_5        thrpt   10   2122477.873 ±  468017.197  ops/s
TxMapCombinerBenchmark.threadScaling_1   thrpt   10   6455423.282 ± 1726579.109  ops/s
TxMapCombinerBenchmark.threadScaling_2   thrpt   10   5712656.338 ±  625715.190  ops/s
TxMapCombinerBenchmark.threadScaling_4   thrpt   10   4656871.168 ±   85689.488  ops/s
TxMapCombinerBenchmark.threadScaling_8   thrpt   10   3727382.982 ±   54456.330  ops/s



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
CombinerBenchmark.combiner_4threads             sem         spin  thrpt   10   4766121.908 ±  246506.884  ops/s
CombinerBenchmark.combiner_4threads             sem         park  thrpt   10   8609374.243 ±  274982.970  ops/s
CombinerBenchmark.combiner_4threads             sem        yield  thrpt   10   4643067.639 ±  291553.856  ops/s
CombinerBenchmark.combiner_4threads             sem    spin-loop  thrpt   10   8916522.915 ±  382107.834  ops/s

### Baseline
Benchmark                                     Mode  Cnt         Score        Error  Units
SynchronizedCombinerBench.combiner_4threads  thrpt   10  17515595.325 ± 910069.618  ops/s

## More Threads/Higher token count
Benchmark                            (combinerType)  (idleStrat)  (tokens)   Mode  Cnt        Score        Error  Units
CombinerBenchmark.combiner_4threads           array         spin       500  thrpt   10   628728.508 ±  29221.238  ops/s
CombinerBenchmark.combiner_4threads           array         park       500  thrpt   10   904079.672 ±  38689.143  ops/s
CombinerBenchmark.combiner_4threads           array        yield       500  thrpt   10   614835.002 ±  27355.834  ops/s
CombinerBenchmark.combiner_4threads           array    spin-loop       500  thrpt   10   687663.741 ±  41021.797  ops/s
CombinerBenchmark.combiner_4threads         unbound         spin       500  thrpt   10   611607.834 ±  25861.686  ops/s
CombinerBenchmark.combiner_4threads         unbound         park       500  thrpt   10  1248335.698 ± 652469.407  ops/s
CombinerBenchmark.combiner_4threads         unbound        yield       500  thrpt   10   649049.601 ±  49499.473  ops/s
CombinerBenchmark.combiner_4threads         unbound    spin-loop       500  thrpt   10   787298.705 ±  42212.581  ops/s
CombinerBenchmark.combiner_4threads             sem         spin       500  thrpt   10   608923.375 ±  62442.940  ops/s
CombinerBenchmark.combiner_4threads             sem         park       500  thrpt   10  1017153.565 ±  73932.567  ops/s
CombinerBenchmark.combiner_4threads             sem        yield       500  thrpt   10   590685.557 ±  52489.373  ops/s
CombinerBenchmark.combiner_4threads             sem    spin-loop       500  thrpt   10   752594.452 ±  85483.183  ops/s

CombinerBenchmark.combiner_8threads           array         spin       500  thrpt   10   500721.652 ±  21420.571  ops/s
CombinerBenchmark.combiner_8threads           array         park       500  thrpt   10   768761.789 ± 166924.699  ops/s
CombinerBenchmark.combiner_8threads           array        yield       500  thrpt   10   462076.420 ±  16345.871  ops/s
CombinerBenchmark.combiner_8threads           array    spin-loop       500  thrpt   10   677144.723 ±  17975.015  ops/s
CombinerBenchmark.combiner_8threads         unbound         spin       500  thrpt   10   507005.600 ±  23656.677  ops/s
CombinerBenchmark.combiner_8threads         unbound         park       500  thrpt   10  1032196.219 ± 483196.423  ops/s
CombinerBenchmark.combiner_8threads         unbound        yield       500  thrpt   10   452305.365 ±  16989.337  ops/s
CombinerBenchmark.combiner_8threads         unbound    spin-loop       500  thrpt   10   708503.064 ±  49134.413  ops/s
CombinerBenchmark.combiner_8threads             sem         spin       500  thrpt   10   485206.218 ±  44766.243  ops/s
CombinerBenchmark.combiner_8threads             sem         park       500  thrpt   10   981706.433 ± 176670.040  ops/s
CombinerBenchmark.combiner_8threads             sem        yield       500  thrpt   10   461104.951 ±  22820.683  ops/s
CombinerBenchmark.combiner_8threads             sem    spin-loop       500  thrpt   10   715079.096 ±  10394.087  ops/s

Benchmark                                    (tokens)   Mode  Cnt       Score       Error  Units
SynchronizedCombinerBench.combiner_4threads       500  thrpt   10  536954.411 ± 30708.466  ops/s
SynchronizedCombinerBench.combiner_8threads       500  thrpt   10  503554.751 ± 36744.837  ops/s
