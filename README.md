# Flat combined transactional map
This transactional map provides both strong isolation and full atomicity guarantees for transactions. Transactions are fully serialized meaning only one transaction can execute at a time, however, each transaction might or might not be run by their own thread, due to the nature of flat combining.
The main goal of this transactional map, is to integrate transactional and flat combining techniques into a map. Transactions in this map are lazy, meaning they aren't processed until commit time. This map provides two combiners to test with
- **Unbound combiner:** This combiner allows an unfixed amount of threads to concurrently access it, however to prevent nodes from growing, a node cleanup is performed infrequently by the combiner after the combiner has been executed a certain amount of times. It is also lock based
- **Semaphore combiner:** This combiner allows a fixed amount of threads to concurrently access it, to prevent nodes from growing, this combiner implements node reuse, however if this combiner is accessed by multiple threads greater than the fixed amount, the queue could grow exponentially large
You can find the raw benchmarks for both combiners [here](txmap-benchmarks/combiner-bmh/combiner-bmh.json)

## Benchmarks
### Contention Benchmarks

Unbound combiner
Benchmark                                 Mode  Cnt        Score        Error  Units
ContentionBenchmark.balanced_1thread     thrpt   10    35757.366 ± 122322.352  ops/s
ContentionBenchmark.balanced_2threads    thrpt   10    38625.053 ±  68046.451  ops/s
ContentionBenchmark.balanced_4threads    thrpt   10   347914.810 ± 350454.468  ops/s
ContentionBenchmark.balanced_8threads    thrpt   10   789153.207 ± 680702.248  ops/s
ContentionBenchmark.readHeavy_1thread    thrpt   10    45780.687 ± 171300.706  ops/s
ContentionBenchmark.readHeavy_2threads   thrpt   10    13955.124 ±   6304.961  ops/s
ContentionBenchmark.readHeavy_4threads   thrpt   10   217778.442 ± 429991.675  ops/s
ContentionBenchmark.readHeavy_8threads   thrpt   10  1215648.069 ± 603733.944  ops/s
ContentionBenchmark.writeHeavy_1thread   thrpt   10    39702.462 ±  86894.867  ops/s
ContentionBenchmark.writeHeavy_2threads  thrpt   10   176123.286 ± 426782.003  ops/s
ContentionBenchmark.writeHeavy_4threads  thrpt   10   259774.303 ± 298855.645  ops/s
ContentionBenchmark.writeHeavy_8threads  thrpt   10   604584.958 ± 367519.210  ops/s

Semaphore Combiner
Benchmark                                 Mode  Cnt       Score        Error  Units
ContentionBenchmark.balanced_1thread     thrpt   10    8691.716 ±   7952.938  ops/s
ContentionBenchmark.balanced_2threads    thrpt   10  311275.172 ± 643421.974  ops/s
ContentionBenchmark.balanced_4threads    thrpt   10  479575.401 ± 586943.131  ops/s
ContentionBenchmark.balanced_8threads    thrpt   10  740289.150 ± 267839.545  ops/s
ContentionBenchmark.readHeavy_1thread    thrpt   10   12509.736 ±   9789.231  ops/s
ContentionBenchmark.readHeavy_2threads   thrpt   10   62504.514 ± 130789.995  ops/s
ContentionBenchmark.readHeavy_4threads   thrpt   10  274208.521 ± 499644.864  ops/s
ContentionBenchmark.readHeavy_8threads   thrpt   10  618049.793 ± 399227.699  ops/s
ContentionBenchmark.writeHeavy_1thread   thrpt   10   12018.321 ±  10948.525  ops/s
ContentionBenchmark.writeHeavy_2threads  thrpt   10   51510.547 ±  93268.852  ops/s
ContentionBenchmark.writeHeavy_4threads  thrpt   10  564511.373 ± 581944.292  ops/s
ContentionBenchmark.writeHeavy_8threads  thrpt   10  757160.797 ± 579021.497  ops/s


### Disjoint Key Benchmarks
Unbound combiner
Benchmark                                    Mode  Cnt        Score        Error  Units
DisjointKeyBenchmark.txMap_batch_16threads  thrpt   10  1676083.607 ± 530431.099  ops/s
DisjointKeyBenchmark.txMap_batch_1thread    thrpt   10   529721.724 ± 704946.588  ops/s
DisjointKeyBenchmark.txMap_batch_2threads   thrpt   10   801321.904 ± 390113.002  ops/s
DisjointKeyBenchmark.txMap_batch_4threads   thrpt   10   663258.157 ± 474961.596  ops/s
DisjointKeyBenchmark.txMap_batch_8threads   thrpt   10  1434117.880 ± 231861.651  ops/s
DisjointKeyBenchmark.txMap_put_16threads    thrpt   10  1031760.017 ± 166761.267  ops/s
DisjointKeyBenchmark.txMap_put_1thread      thrpt   10    88868.037 ± 302785.135  ops/s
DisjointKeyBenchmark.txMap_put_2threads     thrpt   10   133058.011 ± 388475.746  ops/s
DisjointKeyBenchmark.txMap_put_4threads     thrpt   10   159364.036 ± 343323.368  ops/s
DisjointKeyBenchmark.txMap_put_8threads     thrpt   10   743231.779 ± 539693.801  ops/s

Semaphore Combiner
Benchmark                                    Mode  Cnt        Score        Error  Units
DisjointKeyBenchmark.txMap_batch_16threads  thrpt   10  1871074.231 ± 408679.105  ops/s
DisjointKeyBenchmark.txMap_batch_1thread    thrpt   10   725365.709 ± 712724.912  ops/s
DisjointKeyBenchmark.txMap_batch_2threads   thrpt   10   406251.460 ± 549030.757  ops/s
DisjointKeyBenchmark.txMap_batch_4threads   thrpt   10  1345254.118 ± 557789.501  ops/s
DisjointKeyBenchmark.txMap_batch_8threads   thrpt   10  1673726.277 ± 421895.495  ops/s
DisjointKeyBenchmark.txMap_put_16threads    thrpt   10  1058706.972 ± 481730.637  ops/s
DisjointKeyBenchmark.txMap_put_1thread      thrpt   10    72327.738 ± 264812.206  ops/s
DisjointKeyBenchmark.txMap_put_2threads     thrpt   10    14764.175 ±  11418.613  ops/s
DisjointKeyBenchmark.txMap_put_4threads     thrpt   10   391758.037 ± 601290.182  ops/s
DisjointKeyBenchmark.txMap_put_8threads     thrpt   10   714772.917 ± 374110.423  ops/s