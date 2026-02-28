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

