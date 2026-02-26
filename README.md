# Transactional Map
## Pessimistic Transactional Map 
This transactional map's semantics require that rather than readers eagerly stating their intent, readers defer stating their intent till the validation part of the commit phase. Writes however are serialized through one lock per key

## Snapshot Transactional Map
This transactional map's semantics require that rather than readers eagerly stating their intent, readers use dirty reads, meaning readers can see values from uncommited transactions .Writes however are serialized through one lock per key

## Copy on Write Transactional Map
A very straightforward implementation that copies the whole map on each write to make changes to it, tries to CAS, and retries on failure

## Two phase locking
This type of semantic includes two phases
1. Growing: This is the phase when all locks are acquired, then the transaction operations happen. If any individual operation fails everything is rolled back
2. Shrinking: This phase includes the release of all locks sequentially
This semantic emphasizes correctness over concurrency. Acquiring the needed locks for a specific transaction and holding them till the transaction succeeds can hurt performance. This becomes a bigger issue, when an operation in the transaction fails. This leads to wasted work and time spent holding locks, leading to worse perf.

## Semantic concurrency control
A technique that increases the concurrency of db operations by using semantic/operational meaning rather than read write conflicts.
For example given a HashMap and we want to perform a transaction on it. We incorporate the ideas of nested transactions. In the sense that operations are treated as nested transactions that handle their own commit phase.
- Low level open nested transaction - each child transaction commits to a local store buffer containing the child transaction's semantic meaning and well the value the transaction planned to modify/read and obtaining its corresponding read/write lock'
- High level transactions - when the parent transaction eventually commits, it runs using a commit handler, this handler uses the grabbed locks to enforce semantic concurrent control, and runs an abort handler 

### So to summarize this in 3 steps:
1. We take semantic locks on read operations
2. Then, we check for semantic conflicts while writing during commit
3. Then we clear semantic locks on abort and commit


## Implementation details
### Steps
1. The first step is to find semantically dependent operations i.e. operations which should be protected from seeing each other's effects e.g. 
</br> Reads on a key depends on writes on that key, but writes don't depend on reads, therefore writes are independent of reads while reads are dependent on writes
2. We then enforce these dependencies semantically and meaningfully

## Semantic flow of implementation
### Shared Fields
KeyLockers -> These are read transactions operating on a specific key
SizeLockers -> These are read transactions which directly affect the size of the collection



# Benchmarks
## Contention Benchmarks
Pessimistic TxMap
Benchmark                                 Mode  Cnt        Score        Error  Units
ContentionBenchmark.balanced_1thread     thrpt   10   991930.811 ± 224666.114  ops/s
ContentionBenchmark.balanced_2threads    thrpt   10  1237688.796 ± 254630.893  ops/s
ContentionBenchmark.balanced_4threads    thrpt   10   447711.418 ±  19922.407  ops/s
ContentionBenchmark.balanced_8threads    thrpt   10   421867.271 ± 132103.868  ops/s
ContentionBenchmark.readHeavy_1thread    thrpt   10  1382435.216 ± 229931.473  ops/s
ContentionBenchmark.readHeavy_2threads   thrpt   10  1678696.356 ±  79501.504  ops/s
ContentionBenchmark.readHeavy_4threads   thrpt   10  1519950.501 ±  39373.303  ops/s
ContentionBenchmark.readHeavy_8threads   thrpt   10   931462.384 ±  17492.271  ops/s
ContentionBenchmark.writeHeavy_1thread   thrpt   10   952248.582 ± 101669.762  ops/s
ContentionBenchmark.writeHeavy_2threads  thrpt   10  1267212.992 ±  84527.247  ops/s
ContentionBenchmark.writeHeavy_4threads  thrpt   10   395840.268 ±  40371.726  ops/s
ContentionBenchmark.writeHeavy_8threads  thrpt   10   404606.840 ±   7423.453  ops/s

Read Uncommitted
Benchmark                                 Mode  Cnt        Score        Error  Units
ContentionBenchmark.balanced_1thread     thrpt   10  1654203.862 ± 154088.221  ops/s
ContentionBenchmark.balanced_2threads    thrpt   10  2325491.548 ± 321923.612  ops/s
ContentionBenchmark.balanced_4threads    thrpt   10  1373306.325 ± 141031.668  ops/s
ContentionBenchmark.balanced_8threads    thrpt   10  1230180.689 ±  17312.671  ops/s
ContentionBenchmark.readHeavy_1thread    thrpt   10  2004181.748 ± 209672.462  ops/s
ContentionBenchmark.readHeavy_2threads   thrpt   10  2892622.803 ± 642162.674  ops/s
ContentionBenchmark.readHeavy_4threads   thrpt   10  3723099.970 ± 130334.801  ops/s
ContentionBenchmark.readHeavy_8threads   thrpt   10  3104672.078 ±  82370.464  ops/s
ContentionBenchmark.writeHeavy_1thread   thrpt   10  1482714.182 ± 152241.296  ops/s
ContentionBenchmark.writeHeavy_2threads  thrpt   10  2086320.005 ±  46790.834  ops/s
ContentionBenchmark.writeHeavy_4threads  thrpt   10  1394732.474 ± 182261.503  ops/s
ContentionBenchmark.writeHeavy_8threads  thrpt   10  1267503.192 ±  87319.502  ops/s

Copy on Write
Benchmark                                 Mode  Cnt        Score        Error  Units
ContentionBenchmark.balanced_1thread     thrpt   10  3000731.413 ± 162903.256  ops/s
ContentionBenchmark.balanced_2threads    thrpt   10  2611156.559 ± 706467.465  ops/s
ContentionBenchmark.balanced_4threads    thrpt   10  2176038.178 ± 245608.072  ops/s
ContentionBenchmark.balanced_8threads    thrpt   10  1599273.443 ±  25875.287  ops/s
ContentionBenchmark.readHeavy_1thread    thrpt   10  3691160.157 ± 381866.954  ops/s
ContentionBenchmark.readHeavy_2threads   thrpt   10  5131395.788 ± 381973.957  ops/s
ContentionBenchmark.readHeavy_4threads   thrpt   10  6017004.378 ± 232995.400  ops/s
ContentionBenchmark.readHeavy_8threads   thrpt   10  6341228.698 ± 125165.766  ops/s
ContentionBenchmark.writeHeavy_1thread   thrpt   10  2290930.409 ± 276447.081  ops/s
ContentionBenchmark.writeHeavy_2threads  thrpt   10  1719084.529 ±  64252.653  ops/s
ContentionBenchmark.writeHeavy_4threads  thrpt   10  1270065.391 ± 168703.289  ops/s
ContentionBenchmark.writeHeavy_8threads  thrpt   10   921901.993 ±  13774.530  ops/s

## Disjoint Key Benchmarks
Pessimistic
Benchmark                                    Mode  Cnt        Score        Error  Units
DisjointKeyBenchmark.txMap_batch_16threads  thrpt   10  1451705.640 ± 101067.577  ops/s
DisjointKeyBenchmark.txMap_batch_1thread    thrpt   10   690181.069 ±  41481.430  ops/s
DisjointKeyBenchmark.txMap_batch_2threads   thrpt   10   952872.811 ±  58480.421  ops/s
DisjointKeyBenchmark.txMap_batch_4threads   thrpt   10  1193222.032 ±  76247.879  ops/s
DisjointKeyBenchmark.txMap_batch_8threads   thrpt   10  1336009.037 ±  50741.358  ops/s
DisjointKeyBenchmark.txMap_put_16threads    thrpt   10  3445233.331 ± 148131.157  ops/s
DisjointKeyBenchmark.txMap_put_1thread      thrpt   10  1892786.852 ± 205496.580  ops/s
DisjointKeyBenchmark.txMap_put_2threads     thrpt   10  2197675.695 ± 347752.292  ops/s
DisjointKeyBenchmark.txMap_put_4threads     thrpt   10  3091877.275 ± 463701.374  ops/s
DisjointKeyBenchmark.txMap_put_8threads     thrpt   10  3353050.407 ±  93997.324  ops/s

Read Uncommitted
Benchmark                                    Mode  Cnt        Score        Error  Units
DisjointKeyBenchmark.txMap_batch_16threads  thrpt   10  1775809.571 ±  26395.357  ops/s
DisjointKeyBenchmark.txMap_batch_1thread    thrpt   10   959202.970 ± 193575.199  ops/s
DisjointKeyBenchmark.txMap_batch_2threads   thrpt   10  1345322.296 ± 142205.195  ops/s
DisjointKeyBenchmark.txMap_batch_4threads   thrpt   10  1655577.940 ±  55565.755  ops/s
DisjointKeyBenchmark.txMap_batch_8threads   thrpt   10  1687529.896 ± 162429.010  ops/s
DisjointKeyBenchmark.txMap_put_16threads    thrpt   10  3821931.394 ± 350666.102  ops/s
DisjointKeyBenchmark.txMap_put_1thread      thrpt   10  2016823.854 ± 486498.651  ops/s
DisjointKeyBenchmark.txMap_put_2threads     thrpt   10  2895575.040 ± 355517.825  ops/s
DisjointKeyBenchmark.txMap_put_4threads     thrpt   10  3463891.479 ± 268639.010  ops/s
DisjointKeyBenchmark.txMap_put_8threads     thrpt   10  4133484.139 ±  74349.222  ops/s

Copy On Write
Benchmark                                    Mode  Cnt        Score        Error  Units
DisjointKeyBenchmark.txMap_batch_16threads  thrpt   10   305829.381 ±  12605.055  ops/s
DisjointKeyBenchmark.txMap_batch_1thread    thrpt   10  3140472.358 ± 190856.548  ops/s
DisjointKeyBenchmark.txMap_batch_2threads   thrpt   10  1789536.975 ± 120048.456  ops/s
DisjointKeyBenchmark.txMap_batch_4threads   thrpt   10  1045283.907 ±  54499.276  ops/s
DisjointKeyBenchmark.txMap_batch_8threads   thrpt   10   517379.037 ±  17303.101  ops/s
DisjointKeyBenchmark.txMap_put_16threads    thrpt   10   310553.722 ±   8196.530  ops/s
DisjointKeyBenchmark.txMap_put_1thread      thrpt   10  4844667.748 ± 349149.794  ops/s
DisjointKeyBenchmark.txMap_put_2threads     thrpt   10  2228753.121 ± 102717.529  ops/s
DisjointKeyBenchmark.txMap_put_4threads     thrpt   10  1188542.882 ± 104344.036  ops/s
DisjointKeyBenchmark.txMap_put_8threads     thrpt   10   535953.881 ±  11176.792  ops/s



