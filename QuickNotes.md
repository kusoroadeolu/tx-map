## Improving perf of my MVCC TxMap
Initially my MVCC txMap had good read numbers for thrpt and decent write numbers, though the err margins for the write numbers were bad, so I decided to investigate, while investigating, I encountered an issue
1. OOME under contention.
**NOTE:** that Active transactions isnt my garbage collecting algorithm, rather my epoch tracking class, meaning it tracks the current minimum epoch needed for my actual GC thread to clean up old versions
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

After looking through my profile data, I realized that garbage collecting on a writer transaction thread was causing a lot of CPU spikes, so I decided to move GC to a background thread, and the writer txn thread instead submits a cleanup request to the gc thread when the version chain depth reaches a certain threshold
```java
Benchmark                                         Mode  Cnt         Score        Error  Units
ContentionBenchmark.readHeavy_1thread            thrpt   10    864770.978 ± 123348.212  o[profile.jfr](../../../../../jfr-output/io.github.kusoroadeolu.txmap.benchmarks.ContentionBenchmark.readHeavy_8threads-Throughput/profile.jfr)ps/s
ContentionBenchmark.readHeavy_2threads           thrpt   10   1187432.216 ± 171323.762  ops/s
ContentionBenchmark.readHeavy_4threads           thrpt   10    827029.790 ± 525292.066  ops/s
ContentionBenchmark.readHeavy_8threads           thrpt   10    661419.334 ± 215284.213  ops/s
ContentionBenchmark.writeHeavy_1thread           thrpt   10    476757.401 ±  91269.885  ops/s
ContentionBenchmark.writeHeavy_2threads          thrpt   10    622144.154 ± 155951.737  ops/s
ContentionBenchmark.writeHeavy_4threads          thrpt   10    767387.142 ± 171878.776  ops/s
ContentionBenchmark.writeHeavy_8threads          thrpt   10    819022.141 ± 337489.007  ops/s
```

I then decided to cache the min active endTs timestamp in my version chain, to prevent redundant iterations through my version chain
```java
Benchmark                                     Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread        thrpt   10  1384520.967 ± 151058.220  ops/s
ContentionBenchmark.readHeavy_2threads       thrpt   10  1559840.856 ± 361048.003  ops/s
ContentionBenchmark.readHeavy_4threads       thrpt   10  1328777.375 ± 619468.965  ops/s
ContentionBenchmark.readHeavy_8threads       thrpt   10   843850.257 ± 241581.930  ops/s
ContentionBenchmark.writeHeavy_1thread       thrpt   10   527538.045 ± 160021.700  ops/s
ContentionBenchmark.writeHeavy_2threads      thrpt   10   836844.450 ±  95255.683  ops/s
ContentionBenchmark.writeHeavy_4threads      thrpt   10   944841.844 ± 321298.094  ops/s
ContentionBenchmark.writeHeavy_8threads      thrpt   10   764114.028 ± 472135.579  ops/s
```

Looking at my profiled data again, I realized submitting requests to the GC Thread was still a hotspot, so I decided to spread out the frequency in which requests are submitted
```java
Benchmark                                     Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread        thrpt   10  1499641.094 ± 186580.267  ops/s
ContentionBenchmark.readHeavy_2threads       thrpt   10  1800303.740 ± 160148.052  ops/s
ContentionBenchmark.readHeavy_4threads       thrpt   10  1420702.483 ± 566203.194  ops/s
ContentionBenchmark.readHeavy_8threads       thrpt   10   828145.076 ± 393441.472  ops/s
ContentionBenchmark.writeHeavy_1thread       thrpt   10   654820.291 ± 132849.682  ops/s
ContentionBenchmark.writeHeavy_2threads      thrpt   10   895029.640 ± 125955.480  ops/s
ContentionBenchmark.writeHeavy_4threads      thrpt   10   987045.255 ± 437727.460  ops/s
ContentionBenchmark.writeHeavy_8threads      thrpt   10   853466.979 ± 556489.961  ops/s
```

As we can see, the write numbers improved a bit, and the error margins also decreased a bit

I then decided to try segmenting my active transactions tracking class(not my garbage collecting thread) by txn ID, just to measure the difference and I got some surprising results
```java
Benchmark                                     Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread        thrpt   10   601762.093 ± 137771.491  ops/s
ContentionBenchmark.readHeavy_2threads       thrpt   10  1005846.389 ±  96703.066  ops/s
ContentionBenchmark.readHeavy_4threads       thrpt   10  1700100.197 ± 144301.351  ops/s
ContentionBenchmark.readHeavy_8threads       thrpt   10  1321714.189 ± 346860.821  ops/s
ContentionBenchmark.writeHeavy_1thread       thrpt   10   321688.060 ±  95079.876  ops/s
ContentionBenchmark.writeHeavy_2threads      thrpt   10   549510.063 ± 160634.881  ops/s
ContentionBenchmark.writeHeavy_4threads      thrpt   10   826926.873 ± 149437.015  ops/s
ContentionBenchmark.writeHeavy_8threads      thrpt   10  1026862.457 ± 520060.221  ops/s
```
My scaling basically inverted with my lowest thrpt for both operations being at one thread and the highest being at > 2 threads


I then realized maybe using my background threads as virtual threads was causing the high variance, so I decided to run with my background threads as platform threads
**Segmented Active Txn Keeper**
```java
Benchmark                                     Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread        thrpt   10   543983.955 ± 133134.608  ops/s
ContentionBenchmark.readHeavy_2threads       thrpt   10  1020562.096 ± 131954.153  ops/s
ContentionBenchmark.readHeavy_4threads       thrpt   10  1632501.353 ± 197194.523  ops/s
ContentionBenchmark.readHeavy_8threads       thrpt   10  1444733.689 ± 652337.205  ops/s
ContentionBenchmark.writeHeavy_1thread       thrpt   10   335078.800 ± 128993.334  ops/s
ContentionBenchmark.writeHeavy_2threads      thrpt   10   580449.127 ± 110305.596  ops/s
ContentionBenchmark.writeHeavy_4threads      thrpt   10   864819.719 ± 159350.383  ops/s
ContentionBenchmark.writeHeavy_8threads      thrpt   10  1120983.469 ± 783465.405  ops/s
```

**Active Txn Keeper**
```java
ContentionBenchmark.readHeavy_1thread        thrpt   10  1251410.402 ± 170242.439  ops/s
ContentionBenchmark.readHeavy_2threads       thrpt   10  1619555.645 ± 107851.864  ops/s
ContentionBenchmark.readHeavy_4threads       thrpt   10  1345362.000 ± 445030.583  ops/s
ContentionBenchmark.readHeavy_8threads       thrpt   10   784240.432 ± 407925.752  ops/s
ContentionBenchmark.writeHeavy_1thread       thrpt   10   640249.753 ± 110217.595  ops/s
ContentionBenchmark.writeHeavy_2threads      thrpt   10   813339.356 ± 155722.320  ops/s
ContentionBenchmark.writeHeavy_4threads      thrpt   10  1011854.484 ± 452289.869  ops/s
ContentionBenchmark.writeHeavy_8threads      thrpt   10   927350.663 ± 194550.049  ops/s
```


I decided to change my strategy for version tracking to use map based epoch tracking rather than a single writer background thread, and these the results improved significantly, but oddly, at 2 threads, the thrpt is so bad
```java
Benchmark                                     Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread        thrpt   10  2191449.356 ± 405088.856  ops/s
ContentionBenchmark.readHeavy_2threads       thrpt   10   195198.411 ±  37480.037  ops/s
ContentionBenchmark.readHeavy_4threads       thrpt   10  1733823.527 ± 526463.941  ops/s
ContentionBenchmark.readHeavy_8threads       thrpt   10  2832819.684 ± 791310.719  ops/s
ContentionBenchmark.writeHeavy_1thread       thrpt   10   898844.327 ± 184090.618  ops/s
ContentionBenchmark.writeHeavy_2threads      thrpt   10    58322.618 ±   6505.121  ops/s
ContentionBenchmark.writeHeavy_4threads      thrpt   10   118490.002 ±  21500.755  ops/s
ContentionBenchmark.writeHeavy_8threads      thrpt   10  5211745.483 ± 672226.456  ops/s
```

To find the issue, I looked at my profile data, and found `minActiveEpoch()` as a hotspot at 2 threads. The current issue is we scan the entire map for the min active epoch, ideally this shouldn't take too long but as the map grows, could become a bottle neck

I then decided to use a sorted concurrent map for more expensive writes but cheaper reads, and my data actually improved a lot, with stable variance across all thread counts

```java
Benchmark                                     Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread        thrpt   10  2189669.131 ± 219454.082  ops/s
ContentionBenchmark.readHeavy_2threads       thrpt   10  1209368.581 ± 118708.165  ops/s
ContentionBenchmark.readHeavy_4threads       thrpt   10  1390759.022 ± 138734.722  ops/s
ContentionBenchmark.readHeavy_8threads       thrpt   10  1831714.755 ± 209719.251  ops/s
ContentionBenchmark.writeHeavy_1thread       thrpt   10   863360.164 ± 174821.023  ops/s
ContentionBenchmark.writeHeavy_2threads      thrpt   10   695208.420 ± 182427.969  ops/s
ContentionBenchmark.writeHeavy_4threads      thrpt   10   905598.677 ± 107143.027  ops/s
ContentionBenchmark.writeHeavy_8threads      thrpt   10  1438306.517 ± 376373.683  ops/s
```

I then decided to cache and schedule `minEpoch()` reads(at 100ms per read) in my GC thread (rather than reading from the submitting writer txn thread anytime I submit a request to the GC thread), moving the reads of the writer path, but trading thrpt over perfect precision
```java
Benchmark                                     Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread        thrpt   10  2426304.887 ± 259332.240  ops/s
ContentionBenchmark.readHeavy_2threads       thrpt   10  1138292.934 ±  71983.527  ops/s
ContentionBenchmark.readHeavy_4threads       thrpt   10  1297350.055 ± 173150.260  ops/s
ContentionBenchmark.readHeavy_8threads       thrpt   10  1581116.656 ± 142358.242  ops/s
ContentionBenchmark.writeHeavy_1thread       thrpt   10   831511.799 ± 272854.879  ops/s
ContentionBenchmark.writeHeavy_2threads      thrpt   10   651383.441 ± 125000.038  ops/s
ContentionBenchmark.writeHeavy_4threads      thrpt   10   864550.813 ± 102841.781  ops/s
ContentionBenchmark.writeHeavy_8threads      thrpt   10  1133963.803 ± 371369.241  ops/s
```

Since reads were scheduled and cached and off the hotpath (the main writer txn thread), I decided to move back to a normal concurrent hashmap, and compare results
```java
Benchmark                                     Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread        thrpt   10  2033144.625 ± 384180.964  ops/s
ContentionBenchmark.readHeavy_2threads       thrpt   10  1588018.927 ± 270740.481  ops/s
ContentionBenchmark.readHeavy_4threads       thrpt   10  1729477.638 ± 222670.776  ops/s
ContentionBenchmark.readHeavy_8threads       thrpt   10  2155201.736 ± 367418.564  ops/s
ContentionBenchmark.writeHeavy_1thread       thrpt   10   806951.062 ± 230957.839  ops/s
ContentionBenchmark.writeHeavy_2threads      thrpt   10   736237.004 ± 198644.974  ops/s
ContentionBenchmark.writeHeavy_4threads      thrpt   10  1163394.531 ± 183420.198  ops/s
ContentionBenchmark.writeHeavy_8threads      thrpt   10  1664437.392 ± 348898.556  ops/s
```

My current hotspot across all benchmarks right now, is in my `computeIfAbsent()` call, anytime a transaction(at creation time) requests for their current epoch(i.e. tBegin)



## Zipfian Bench
```java
Benchmark                                               Mode  Cnt         Score        Error  Units
ZipfianBenchmark.highSkew_readHeavy_1thread            thrpt   10   1393606.446 ± 437895.917  ops/s
ZipfianBenchmark.highSkew_readHeavy_1thread:aborts     thrpt   10           ≈ 0                   #
ZipfianBenchmark.highSkew_readHeavy_1thread:commits    thrpt   10  14110012.000                   #
ZipfianBenchmark.highSkew_readHeavy_2threads           thrpt   10   1358232.039 ± 305299.879  ops/s
ZipfianBenchmark.highSkew_readHeavy_2threads:aborts    thrpt   10     39006.000                   #
ZipfianBenchmark.highSkew_readHeavy_2threads:commits   thrpt   10  13594051.000                   #
ZipfianBenchmark.highSkew_readHeavy_4threads           thrpt   10   1354410.254 ± 370369.142  ops/s
ZipfianBenchmark.highSkew_readHeavy_4threads:aborts    thrpt   10     97559.000                   #
ZipfianBenchmark.highSkew_readHeavy_4threads:commits   thrpt   10  13797113.000                   #
ZipfianBenchmark.highSkew_readHeavy_8threads           thrpt   10   2431678.956 ± 306186.383  ops/s
ZipfianBenchmark.highSkew_readHeavy_8threads:aborts    thrpt   10    327754.000                   #
ZipfianBenchmark.highSkew_readHeavy_8threads:commits   thrpt   10  24191452.000                   #
ZipfianBenchmark.highSkew_writeHeavy_1thread           thrpt   10    492155.116 ± 171078.481  ops/s
ZipfianBenchmark.highSkew_writeHeavy_1thread:aborts    thrpt   10           ≈ 0                   #
ZipfianBenchmark.highSkew_writeHeavy_1thread:commits   thrpt   10   4974842.000                   #
ZipfianBenchmark.highSkew_writeHeavy_2threads          thrpt   10    481699.851 ± 213931.862  ops/s
ZipfianBenchmark.highSkew_writeHeavy_2threads:aborts   thrpt   10     79433.000                   #
ZipfianBenchmark.highSkew_writeHeavy_2threads:commits  thrpt   10   4787212.000                   #
ZipfianBenchmark.highSkew_writeHeavy_4threads          thrpt   10    806222.398 ± 152026.921  ops/s
ZipfianBenchmark.highSkew_writeHeavy_4threads:aborts   thrpt   10    214219.000                   #
ZipfianBenchmark.highSkew_writeHeavy_4threads:commits  thrpt   10   7982397.000                   #
ZipfianBenchmark.highSkew_writeHeavy_8threads          thrpt   10    980224.466 ± 266672.937  ops/s
ZipfianBenchmark.highSkew_writeHeavy_8threads:aborts   thrpt   10    556365.000                   #
ZipfianBenchmark.highSkew_writeHeavy_8threads:commits  thrpt   10  10289137.000                   #
ZipfianBenchmark.lowSkew_readHeavy_1thread             thrpt   10   1815250.914 ± 283742.915  ops/s
ZipfianBenchmark.lowSkew_readHeavy_1thread:aborts      thrpt   10           ≈ 0                   #
ZipfianBenchmark.lowSkew_readHeavy_1thread:commits     thrpt   10  18161640.000                   #
ZipfianBenchmark.lowSkew_readHeavy_2threads            thrpt   10   1438418.394 ± 580858.650  ops/s
ZipfianBenchmark.lowSkew_readHeavy_2threads:aborts     thrpt   10      5742.000                   #
ZipfianBenchmark.lowSkew_readHeavy_2threads:commits    thrpt   10  14390301.000                   #
ZipfianBenchmark.lowSkew_readHeavy_4threads            thrpt   10   2153052.255 ± 198120.497  ops/s
ZipfianBenchmark.lowSkew_readHeavy_4threads:aborts     thrpt   10     25534.000                   #
ZipfianBenchmark.lowSkew_readHeavy_4threads:commits    thrpt   10  21553704.000                   #
ZipfianBenchmark.lowSkew_readHeavy_8threads            thrpt   10   2064802.928 ± 798420.267  ops/s
ZipfianBenchmark.lowSkew_readHeavy_8threads:aborts     thrpt   10     46260.000                   #
ZipfianBenchmark.lowSkew_readHeavy_8threads:commits    thrpt   10  22478964.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_1thread            thrpt   10    343656.803 ± 301494.466  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_1thread:aborts     thrpt   10           ≈ 0                   #
ZipfianBenchmark.lowSkew_writeHeavy_1thread:commits    thrpt   10   3493757.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_2threads           thrpt   10    663259.983 ± 183178.864  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_2threads:aborts    thrpt   10      8035.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_2threads:commits   thrpt   10   6635659.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_4threads           thrpt   10    880624.208 ± 218307.010  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_4threads:aborts    thrpt   10     33303.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_4threads:commits   thrpt   10   8854223.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_8threads           thrpt   10    895477.631 ± 281876.580  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_8threads:aborts    thrpt   10    100709.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_8threads:commits   thrpt   10  10482891.000                   #
```