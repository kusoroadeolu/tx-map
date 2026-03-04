Rethought about what I'm actually benchmarking, I realized that since operations are essentially serialized, trying to measure the effects of read/write heavy uploads per key is basically useless, so I redesigned my benchmarks to measure these instead
1. The change in throughput as the number of threads increased
2. The change in throughput of the number of operations per transaction

Later on I realized my code had a significant flaw, each transaction created its own individual combiner which meant operations weren't fully serialized rendering my inital benchmarks(from my first article) useless, my tests were supposed to catch that no idea how that got through
With these changes and the introduction of my atomic array combiner(which even with the old benchmarks showed tighter variance, compared to unbound and semaphore), I plan to rerun these tests. I also optimized the semaphore combiner to ditch the volatile semantics and use plain writes with strong visibility guarantees but no reordering guarantees

**TODO:** Investigate, why did a volatile read after the combiner scanned the pub queue/arr, while still holding the lock, fix the issue where threads hung on their condition indefinitely

**TODO Completed:** Changed the logic from pointer tracking to just rescanning the entire array when a thread becomes the combiner, looks like the previous issue wasn't actually a memory visibility issue(thankfully), rather an issue where combiners couldn't progress after hitting a null cell, because the cell never got filled



## Benchmark notes
### Measuring with different idle strategies
Here, note we aren't measuring the individual throughput of each combiner, however we are measuring the thrpt and variance of each combiner based on their idle strategy and picking the most consistent and performant strategy 

We're measuring 3 combiner implementations(semaphore, atomic-array and unbound) with different idle strategies using `Blackhole.consumeCPU(500 tkns)` as the action applied to prevent the JIT from optimizing the work applied.
Across all combiner types we're measuring their scaling value at 4 threads up to 8 threads to get a clearer picture of their thrpt as contention grows. As a baseline I decided to use a fat lock `sync` combiner as a baseline to measure against the combining overhead of other combiners


At 4 threads, `park` idle strategy has the highest thrpt across all combiner impl however it's variance is also the highest across all idle strats. The spin loop follows close in second with the second highest thrpt and very tight error margins (around >= ~10%) across all threads. The spin and yield idle strategies, actually fail to break past the 700kops/s margin though they have tight error margins as well.
At 8 threads, the thrpt for `park` reduces by almost ~20% across all impl, however the thrpt for `spin-loop` decrease  by ~5% to ~7% across all impl. The thrpt for both `spin` and `yield` decrease by almost ~15% across all impl  

Therefore, `spin-loop` is chosen as our default idle strategy for other benchmarks due to its favorable thrpt, low variance and graceful degradation across all impl under high contention

### Measuring serialized combined map across different impl strats
Here we measure how throughput varies as the number of operations per transaction increases on 4 threads(cause this is an 8 core machine) and how throughput varies as number of threads increases with a cap of one operation per transaction. We use a synchronized combiner as our baseline

#### Ops per transaction (Measured at 4 threads steady)
At one operation per transaction, the thrpt for the sync combiner surpasses other combiners by ~20% to ~31% however as ops per tx increases to 10, the combiners thrpt drops to ~1.4Mops/s while the sync combiner drops to ~1.1Mops/s around a ~22% gap between the sync combiner and the other combiners
The batching and handoff mechanisms of the combiners amortizes(reduces) the combining overhead over multiple operations while the sync combiner has to pay the cost of obtaining a lock per transaction 

#### Thrpt as num of transactions increases(Measured with 2 ops per tx)
At one thread(under low contention), unbound combiner shows the highest thrpt at ~8.5M ops/s(though with a high variance of 800K ops/s) with sync showing the lowest at 6.3M ops/s. Array and Node cycling combiner both show ~6.9M ops/s under low contention.
At 8 threads(under high contention), sync combiner shows the highest thrpt at 5.7Mops/s with the array combiner showing the lowest thrpt overall at 3.8M ops/s. Both the Node cycling and unbound combiner degrade gracefully to 4.1M ops/s and 4.3M ops/s respectively;
Worth noting that a simple lock under high contention benefits from the JVM's lock coarsening and biased locking optimizations, whereas the combining overhead of the other combiners doesn't get those same benefits.


### Measuring segmented combined map across different impl strats
Here we're measuring if segmenting combiners per key improves thrpt, regardless of the combiner type, though that might play a factor, hence we're testing all combiners for this
Using a synchronized combiner(basically a locked) combiner per key as a baseline

#### Ops per transaction (Measured at 4 threads steady)
At one op per tx, compared to the serialized maps, no impl manages to break past the 3M ops/s threshold, and the thrpt only gets worse at 10 ops per tx as the best impl only manages to barely scrape ~800k ops/s

#### Thrpt as num of transactions increases(Measured with 2 ops per tx)
Under low contention, compared to the serialized maps whose best impl has a thrpt of ~8M ops/s, no impl manages to break past 5M ops/s , and the thrpt only get worse under high contention as the best impl only manages to barely scrape ~2M ops/s while the best serialized combined map has 5M ops/s.
The per-key batching benefit is outweighed by the overhead of multiple combine calls per transaction, since fewer operations share each serialization point compared to the fully serialized map. Also, the flat combined map wraps all ops in a single `combine()` call paying the overhead once per transaction, while the segmented map pays it once per unique key per transaction.