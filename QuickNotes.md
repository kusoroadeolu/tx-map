## Improving perf of my MVCC TxMap
Initially my MVCC txMap had good read numbers for thrpt and decent write numbers, though the err margins for the write numbers were bad, so I decided to investigate, while investigating, I encountered an issue
1. OOME under contention.
Note that Active transactions isnt my garbage collecting algorithm, rather my epoch tracking class, meaning it tracks the current minimum epoch needed for my GC to clean up old versions
```java
ActiveTransactions activeTxns = mvccTx.map.activeTransactions.copy(); //We're getting a copy to prevent any race conditions while we're searching for the lowest tBegin
long minActiveTBegin = activeTxns.findMinActiveTBegin(); 
versionChain.removeUnreachableVersions(minActiveTBegin);

// In the ActiveTransactions call
long findMinActiveTBegin(){
    Set<Long> set = new HashSet<>(map.values()); //Copy to set to prevent duplicates and min traversals, causes an OOME!!
    long min = 0;
    int count = 0;
    for (long l : set){
        if (count == 0 || l < min){
            min = l;
        }
        ++count;
    }

    return min;
}
```
This line of code causes an OOME, under contention the active transactions map could be very large, if we're copying and iterating it anytime we want to remove an old version, issues could occur. To fix this, i'll just use a single writer that automatically tracks the min active begin ts, though under contention the writer might not keep up, its still better than copying on every write lol


After I fixed this OOME with a single writer design, my write numbers tanked to around **< 20k ops/s** per bmh. I decided to do some profiling to isolate the thrpt killer and I realized it was my `ActiveTransactions#remove` call, rather than submitting a remove request to the queue, it was actually removing a non-existent request from the queue leading to O(N) calls every transaction commit
.Plus, since we were never actually removing anything from the map, the minTBegin never changed, meaning my gc epoch reclamation was doing meaningless work after while. After updating this code to properly work, by benchmark results actually improved substantially

**Before**
```java
"Benchmark                                         Mode  Cnt      Score      Error  Units
ContentionBenchmark.readHeavy_1thread            thrpt   10   1248.762 ±  921.068  ops/s
ContentionBenchmark.readHeavy_2threads           thrpt   10   1205.265 ±  413.555  ops/s
ContentionBenchmark.readHeavy_4threads           thrpt   10   1407.078 ±  528.927  ops/s
ContentionBenchmark.readHeavy_8threads           thrpt   10   2566.666 ±  610.823  ops/s
ContentionBenchmark.writeHeavy_1thread           thrpt   10   2021.587 ±  487.228  ops/s
ContentionBenchmark.writeHeavy_2threads          thrpt   10   1475.348 ±  388.932  ops/s
ContentionBenchmark.writeHeavy_4threads          thrpt   10   1693.183 ±  532.851  ops/s
ContentionBenchmark.writeHeavy_8threads          thrpt   10  13806.692 ± 4179.536  ops/s

```

**After**
```java
Benchmark                                         Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread            thrpt   10   454138.457 ±  92558.035  ops/s
ContentionBenchmark.readHeavy_2threads           thrpt   10   449543.650 ± 226056.899  ops/s
ContentionBenchmark.readHeavy_4threads           thrpt   10   623248.848 ± 177717.798  ops/s
ContentionBenchmark.readHeavy_8threads           thrpt   10   464862.805 ± 234641.969  ops/s
ContentionBenchmark.writeHeavy_1thread           thrpt   10   369462.166 ±  79723.306  ops/s
ContentionBenchmark.writeHeavy_2threads          thrpt   10   445274.916 ± 171877.221  ops/s
ContentionBenchmark.writeHeavy_4threads          thrpt   10   728382.685 ± 174790.985  ops/s
ContentionBenchmark.writeHeavy_8threads          thrpt   10   902537.739 ±  93291.275  ops/s
```


After another round of profiling, I realized that I was making `findOverlap()` calls frequently on my read heavy transactions, so basically an O(n) traversal for each find overlap call, which just becomes worse as the version queue per key grows, so I decided to use a different approach, I decided to use a navigable map as my version chain to reduce this traversal time per call to O(logN), and the numbers showed significant improvement
```java
Benchmark                                         Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread            thrpt   10   601083.402 ± 165550.604  ops/s
ContentionBenchmark.readHeavy_2threads           thrpt   10   642442.703 ± 187617.695  ops/s
ContentionBenchmark.readHeavy_4threads           thrpt   10   688103.749 ±  89658.431  ops/s
ContentionBenchmark.readHeavy_8threads           thrpt   10   690432.423 ± 101032.453  ops/s
ContentionBenchmark.writeHeavy_1thread           thrpt   10   454926.218 ± 184083.441  ops/s
ContentionBenchmark.writeHeavy_2threads          thrpt   10   594926.342 ±  92822.567  ops/s
ContentionBenchmark.writeHeavy_4threads          thrpt   10   836488.562 ±  80008.830  ops/s
ContentionBenchmark.writeHeavy_8threads          thrpt   10   979821.239 ±  89332.281  ops/s
```

After looking through my code again, I realized I never actually started the background worker thread for my `ActiveTransactions` class, so i decided to start it and well looks like we're back at the beginning lol
```java
Benchmark                                         Mode  Cnt         Score        Error  Units
ContentionBenchmark.readHeavy_1thread            thrpt   10     37911.129 ±  13675.765  ops/s
ContentionBenchmark.readHeavy_2threads           thrpt   10     89182.952 ±  16428.530  ops/s
ContentionBenchmark.readHeavy_4threads           thrpt   10   1197666.295 ± 370356.847  ops/s
ContentionBenchmark.readHeavy_8threads           thrpt   10    868822.634 ± 334063.540  ops/s
ContentionBenchmark.writeHeavy_1thread           thrpt   10      2849.386 ±   1326.803  ops/s
ContentionBenchmark.writeHeavy_2threads          thrpt   10      9548.036 ±   6833.268  ops/s
ContentionBenchmark.writeHeavy_4threads          thrpt   10     25872.105 ±  13197.781  ops/s
ContentionBenchmark.writeHeavy_8threads          thrpt   10   1132311.163 ± 285098.340  ops/s
```