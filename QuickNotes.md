## Improving perf of my MVCC TxMap
Initially my MVCC txMap had good read numbers for thrpt and decent write numbers, though the err margins for the write numbers were bad, so I decided to investigate, while investigating, I encountered an issue
1. OOME under contention.
**NOTE:** that Active transactions isnt my garbage collecting algorithm, rather my epoch tracking class, meaning it tracks the current minimum epoch needed for my actual GC thread to clean up old versions
```java
ActiveTransactions activeTxns = mvccTx.map.activeTransactions.copy(); //Copied the entire map on active txns, could be thousands 
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


After another round of profiling, I realized that I was making `findOverlap()` calls frequently on my read heavy transactions, so basically an O(n) traversal for each find overlap call, which just becomes worse as the version queue per key grows, so I decided to use a different approach, I decided to use a navigable map as my version chain to reduce this traversal time per call to O(logN), at the cost of more expensive writes, and the numbers showed significant improvement
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

After looking through my code again, I realized I never actually started the background worker thread for my `ActiveTransactions` class, so i decided to start it and well it looks like we're back at the beginning lol
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
ContentionBenchmark.readHeavy_1thread            thrpt   10    864770.978 ± 123348.212  ops/s
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

Navigable version chain
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

After looking at my queue version chain, I realized calling size() to check version chain depth on each write txn was killing perf, since we had to scan the whole queue to find the size(even with the queue's dead nodes), so I decided to use a long adder to track the size for O(1) calls
Queue version chain
```java
Benchmark                                     Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread        thrpt   10  2290892.594 ± 491972.988  ops/s
ContentionBenchmark.readHeavy_2threads       thrpt   10  1604327.428 ± 208550.678  ops/s
ContentionBenchmark.readHeavy_4threads       thrpt   10  1891341.883 ± 250730.085  ops/s
ContentionBenchmark.readHeavy_8threads       thrpt   10  1937383.908 ± 501990.327  ops/s
ContentionBenchmark.writeHeavy_1thread       thrpt   10   932853.659 ± 165703.963  ops/s
ContentionBenchmark.writeHeavy_2threads      thrpt   10   966232.277 ± 279759.248  ops/s
ContentionBenchmark.writeHeavy_4threads      thrpt   10  1135401.769 ± 380147.279  ops/s
ContentionBenchmark.writeHeavy_8threads      thrpt   10  1620440.108 ± 446644.869  ops/s
```

I saw my current hotspot across all benchmarks right now, is in my `computeIfAbsent()` call, anytime a transaction(at creation time) requests for their current epoch(i.e. tBegin)
To combat this, I decided to try something a bit different, I decided to build a thread local epoch tracker, to handle to compute if absent hotpath, though this is only paired best with small pool of N platform threads, and the results were much better
1. Unlike the previous `DefaultEpochTracker`(mapped by epoch to all active transactions), which tracks all active transactions regardless of thread id(hence higher contention but more suitable for v threads), this epoch tracker maps the id of each thread to the current epoch of the transaction its hosting, once a transaction has ended, it maps it back to a dummy value which notifies us that this thread isnt actively participating in a txn and we should dont includ it in our min active epochs
2. Since keys can literally not be contested(hence less locked waits to write to an epoch), since each key is mapped to a thread, performance increases a good amount 

```java
Benchmark                                     Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread        thrpt   10  2887534.127 ± 554342.591  ops/s
ContentionBenchmark.readHeavy_2threads       thrpt   10  2366272.494 ± 324415.185  ops/s
ContentionBenchmark.readHeavy_4threads       thrpt   10  2162652.894 ± 276947.370  ops/s
ContentionBenchmark.readHeavy_8threads       thrpt   10  2449227.477 ± 443072.582  ops/s
ContentionBenchmark.writeHeavy_1thread       thrpt   10  1271287.026 ± 498230.994  ops/s
ContentionBenchmark.writeHeavy_2threads      thrpt   10  1578855.855 ± 213555.495  ops/s
ContentionBenchmark.writeHeavy_4threads      thrpt   10  1340979.654 ± 316184.797  ops/s
ContentionBenchmark.writeHeavy_8threads      thrpt   10  1519457.062 ± 700083.591  ops/s
```
The variance was actually worse in some scenarios, almost 100% of the actual score

While running these benchmarks, I noticed something odd looking at my profile data for memory allocation, around a lot of memory was getting allocated but barely cleaned up by the GC while running my benchmarks, leading to high variance and my numbers tanking in unusual ways during benchmarking. The profile data showed most of this occurred when I started a txn but that wasn't too helpful. So I decided to look at my actual memory usage when running these benchmarks and I noticed around 95% of my memory was being used while running these benchmarks.
My first suspect were my version chains, since they could be the main issue objects were not being cleaned up by the GC. So I decided to add some debug statements to see if versions we're being cleaned up, and they actually weren't. The issue lied in this simple if check I added earlier to prevent redundant O(N) lookups

```java
    @Override
public void removeUnreachableVersions(long tBegin) {
    if (tBegin <= minVisibleEpoch.epoch) return; //This simple line here, the issue was that minVisibleEpoch was always initialized as Long.MAX_VALUE, even if the epoch could be updated while non-visible version were getting pruned, the GC would never actually get the chance to prune those versions, because beginTs(seen epoch) would always be less than Long.MAX_VALUE
    minVisibleEpoch.reset(); //Reset the holder everytime, to prevent a situation where we are sitting on an older end ts, from a version pruned a while back
    var ls = this.latest;
    Set<Map.Entry<Long, Version<E>>> set = versionMap.entrySet();


    
    set.removeIf(entry -> {
        var val = entry.getValue();
        boolean shouldRemove = val.endTs < tBegin  && val != ls;

        if (!shouldRemove && val.endTs < minVisibleEpoch.epoch) minVisibleEpoch.epoch = val.endTs;
        return shouldRemove;
    });
}
```

This was changed to
```java
if (minVisibleEpoch.epoch != Long.MAX_VALUE && tBegin <= minVisibleEpoch.epoch) return; //Actually gave the gc a chance to prune the older versions
```

After this, my numbers improved significantly and variance reduced a bit
```java
Benchmark                                    (versionChainType)   Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread                     queue  thrpt   10  3022274.294 ± 397643.609  ops/s
ContentionBenchmark.readHeavy_1thread                       nav  thrpt   10  2250631.595 ± 201546.981  ops/s
ContentionBenchmark.readHeavy_2threads                    queue  thrpt   10  2583840.733 ± 444871.511  ops/s
ContentionBenchmark.readHeavy_2threads                      nav  thrpt   10  2291496.019 ± 304662.994  ops/s
ContentionBenchmark.readHeavy_4threads                    queue  thrpt   10  3152546.075 ± 350295.696  ops/s
ContentionBenchmark.readHeavy_4threads                      nav  thrpt   10  2933701.271 ± 210834.860  ops/s
ContentionBenchmark.readHeavy_8threads                    queue  thrpt   10  4209719.728 ± 611222.951  ops/s
ContentionBenchmark.readHeavy_8threads                      nav  thrpt   10  4058722.339 ± 265986.981  ops/s
ContentionBenchmark.writeHeavy_1thread                    queue  thrpt   10  2467899.289 ± 343129.882  ops/s
ContentionBenchmark.writeHeavy_1thread                      nav  thrpt   10  1224346.814 ± 276528.783  ops/s
ContentionBenchmark.writeHeavy_2threads                   queue  thrpt   10  1987672.075 ± 142924.001  ops/s
ContentionBenchmark.writeHeavy_2threads                     nav  thrpt   10  1474317.537 ± 332782.403  ops/s
ContentionBenchmark.writeHeavy_4threads                   queue  thrpt   10  2540089.406 ± 107245.569  ops/s
ContentionBenchmark.writeHeavy_4threads                     nav  thrpt   10  1941961.678 ± 335538.076  ops/s
ContentionBenchmark.writeHeavy_8threads                   queue  thrpt   10  3310156.822 ± 319971.721  ops/s
ContentionBenchmark.writeHeavy_8threads                     nav  thrpt   10  3599563.143 ± 712225.130  ops/s
```

I was still a bit skeptical about the variance, even though it was pretty reasonable, I realized a lot of memory was getting allocated to my thread local epoch tracker under contention due to `long` boxing when updating epochs, so I decided to try using fast utils synchronized `Long2LongHashMap`, to prevent boxing and allocations under high contention, and rerunning the benchmarks again, allocation on that hotpath dropped to basically zero, and writes under contention suffered a bit, but the variance and read heavy workloads were pretty good

```java
Benchmark                                    (versionChainType)   Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread                     queue  thrpt   10  3917121.556 ± 191182.041  ops/s
ContentionBenchmark.readHeavy_1thread                       nav  thrpt   10  2592747.908 ± 192348.108  ops/s
ContentionBenchmark.readHeavy_2threads                    queue  thrpt   10  2935324.852 ± 380159.702  ops/s
ContentionBenchmark.readHeavy_2threads                      nav  thrpt   10  2587901.962 ± 253365.744  ops/s
ContentionBenchmark.readHeavy_4threads                    queue  thrpt   10  2410564.690 ± 394036.694  ops/s
ContentionBenchmark.readHeavy_4threads                      nav  thrpt   10  2425199.868 ± 109061.792  ops/s
ContentionBenchmark.readHeavy_8threads                    queue  thrpt   10  2138869.830 ± 106605.873  ops/s
ContentionBenchmark.readHeavy_8threads                      nav  thrpt   10  2006265.789 ± 154944.074  ops/s
ContentionBenchmark.writeHeavy_1thread                    queue  thrpt   10  2789016.664 ± 258145.402  ops/s
ContentionBenchmark.writeHeavy_1thread                      nav  thrpt   10  1247223.769 ± 212243.516  ops/s
ContentionBenchmark.writeHeavy_2threads                   queue  thrpt   10  2308032.198 ± 234208.698  ops/s
ContentionBenchmark.writeHeavy_2threads                     nav  thrpt   10  1395297.149 ± 187661.268  ops/s
ContentionBenchmark.writeHeavy_4threads                   queue  thrpt   10  2285734.168 ± 222636.258  ops/s
ContentionBenchmark.writeHeavy_4threads                     nav  thrpt   10  1738846.900 ± 503307.766  ops/s
ContentionBenchmark.writeHeavy_8threads                   queue  thrpt   10  2274919.824 ±  46946.253  ops/s
ContentionBenchmark.writeHeavy_8threads                     nav  thrpt   10  1893349.970 ± 213244.636  ops/s
```

The thrpt was alright, though after some research I found out about a generics trick, using primitive arrays as generic types, so instead of boxed long values. I decided to try this out with CHM and compare it to the serialized long2long version
```java
ConcurrentMap<Long, Long> map //Instead of this, we could do
ConcurrentMap<Long, long[]> map //No boxing for values
```

```java
Benchmark                                    (versionChainType)   Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread                     queue  thrpt   10  3921045.464 ± 379095.211  ops/s
ContentionBenchmark.readHeavy_1thread                       nav  thrpt   10  2490400.560 ± 235824.152  ops/s
ContentionBenchmark.readHeavy_2threads                    queue  thrpt   10  3351486.812 ± 330871.332  ops/s
ContentionBenchmark.readHeavy_2threads                      nav  thrpt   10  2961814.543 ± 271271.445  ops/s
ContentionBenchmark.readHeavy_4threads                    queue  thrpt   10  4130101.194 ± 365901.882  ops/s
ContentionBenchmark.readHeavy_4threads                      nav  thrpt   10  3967520.781 ± 267639.783  ops/s
ContentionBenchmark.readHeavy_8threads                    queue  thrpt   10  5477847.548 ± 656616.321  ops/s
ContentionBenchmark.readHeavy_8threads                      nav  thrpt   10  5384214.753 ± 396929.168  ops/s
ContentionBenchmark.writeHeavy_1thread                    queue  thrpt   10  2948891.898 ± 461806.810  ops/s
ContentionBenchmark.writeHeavy_1thread                      nav  thrpt   10  1256910.691 ± 203279.439  ops/s
ContentionBenchmark.writeHeavy_2threads                   queue  thrpt   10  2523596.142 ± 196569.443  ops/s
ContentionBenchmark.writeHeavy_2threads                     nav  thrpt   10  1408471.703 ± 192463.826  ops/s
ContentionBenchmark.writeHeavy_4threads                   queue  thrpt   10  2943223.429 ± 276821.883  ops/s
ContentionBenchmark.writeHeavy_4threads                     nav  thrpt   10  2577599.074 ± 714794.780  ops/s
ContentionBenchmark.writeHeavy_8threads                   queue  thrpt   10  4064740.729 ± 416730.295  ops/s
ContentionBenchmark.writeHeavy_8threads                     nav  thrpt   10  4574077.728 ± 549287.881  ops/s
```




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


Since, I've been testing thrpt for my mvcc map for "best case scenarios" i.e. without retries on aborts, so I decided to test with retries on abort. Note that this was base-lined against my map with a QueueVersionChain and Long2ArrayEpochTracker
```java
Benchmark                                    (versionChainType)   Mode  Cnt        Score        Error  Units
ContentionBenchmark.readHeavy_1thread                     queue  thrpt   10  1210064.106 ± 320644.732  ops/s
ContentionBenchmark.readHeavy_2threads                    queue  thrpt   10  1511181.782 ± 234672.283  ops/s
ContentionBenchmark.readHeavy_4threads                    queue  thrpt   10  2200939.214 ± 312049.219  ops/s
ContentionBenchmark.readHeavy_8threads                    queue  thrpt   10  2836487.345 ± 380214.626  ops/s
ContentionBenchmark.writeHeavy_1thread                    queue  thrpt   10   941607.296 ± 299855.822  ops/s
ContentionBenchmark.writeHeavy_2threads                   queue  thrpt   10  1236560.892 ± 240710.149  ops/s
ContentionBenchmark.writeHeavy_4threads                   queue  thrpt   10  1390331.262 ± 306751.935  ops/s
ContentionBenchmark.writeHeavy_8threads                   queue  thrpt   10  1193581.306 ± 191506.103  ops/s
```

To fully understand this drop I compared profile data from this benchmark to those w/o retries. While everything looked **similarish** on the CPU side, however memory was a different story with memory usage spiking up from ~16GB to almost ~30GB at every iteration. Due to the frequency of aborts, for each retry, a new txn had to be created, meaning more memory allocated for the txn object, its operations, its completable values and at commit, hence more pressure on the GC(Java's GC), hence more GC pauses and lower thrpt 