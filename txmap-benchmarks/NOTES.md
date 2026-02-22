# Notes on benchmark results

# Throughput as contention reduces
## Disjoint keys
This benchmark includes comparing the workload of 3 transactional map implementations, in the case, in which keys are not contended and multiple threads are interacting with the map.
It extends from simple put operations on uncontended keys to batch operations including 2 writes and one read spanning from 1 thread to 16 threads, in hope of capturing how the Atomicity and Isolation guarantees my transactional maps impose, affect their performance

### Baseline Benchmark
The baseline benchmark includes a single put operation then batch(which are write heavy) disjoint key operations on a concurrent hashmap. 

### TxMap Results
#### Copy on Write
Under no key contention, batch operations on the copy on write map scale poorly as thread count increase, with throughput dropping almost 90%, from `2,787,844.572` at 1 thread to `296828.9312775262` at 16 threads and the sharpest drop occurring at 4 threads, around a 39% drop in throughput. This can be attributed to CAS inherent limitation under write contention and the extra overhead of copying the entire map on each spin, especially when dealing with multiple keys , leading to lower throughput across the board.

The write operations, which include a single `put()` call,  tell a similar story, with throughput dropping almost 93% from `4674494.003` at 1 thread to `303442` at 16 threads

#### Pessimistic
Batch operations actually scale linearly as thread count increase by about 60% going from  `575998.780` at 1 thread, peaking at `1185412.118` at 8 threads and falling back to `926813.453` at 16 threads.

Write operations also linearly scale, peaking `3082616.565` at 8 threads and falling at 16 threads `2895138.401`. This is due to the limitation of 8 cores of my machine, limiting true parallelism at 16 threads. Apart from that, this can also be attributed to the fact that less lock contention allows for higher throughput across the board when writing to the map 
Though, I do believe these write numbers could be higher, if I refactor the spin loop in the write lock, into something much more cpu efficient

#### Snapshot
The numbers for the snapshot tx-map, are actually more consistent with balanced workload scaling from `992184.325` at 1 thread to `1757920.671` at 16 threads almost a 44% throughput increase!



### Contended Keys
This benchmark includes comparing the workload of 3 transactional map implementations, in the case, in which 4 shared keys are heavily contended and multiple threads are interacting with the map.

#### Copy on Write TxMap 
Under contention, the copy on write implementation's performance is heavily shaped by the read/write ratio.
Read-heavy workloads actually scale well, throughput grows from ~3.7M ops/s at 1 thread to 6.4M at 8 threads (+72%), since readers don't block each other at all.
Balanced workloads (50/50) are more interesting, throughput peaks at 2 threads (2.8M) then degrades steadily to ~1.6M at 8 threads. The 1-thread result (~2.2M) also has a huge error margin (±1M), suggesting the JVM hadn't fully warmed up or the two forks behaved very differently, so take that one with a grain of salt.
Write-heavy tells the expected story, contention hurts. Throughput drops ~60% from 1 thread (2.4M) to 8 threads (941K), with the sharpest drop happening immediately at 2 threads (-28%). CAS contention on a small key pool is the bottleneck here.

#### Pessimistic TxMap 
Under contention, the pessimistic implementation's performance is also heavily shaped by the read/write ratio.
Read-heavy workloads scales poorly, throughput drops from ~1.2M ops/s at 1 thread to ~865k at 8 threads. This is mainly due to the isolation guarantees this impl provides, blocking readers while writes are active and vice versa
Balanced workloads (50/50) are more interesting, throughput drops ~55% from 1 thread (~933k) to 8 threads (~418k). The 1-thread result also has a huge error margin (±400k), suggesting the JVM hadn't fully warmed up or the two forks behaved very differently, so take that one with a grain of salt as well. This is probably due to the spin wait loop mechanism while writes wait for readers to complete
Write-heavy tells the expected story, contention hurts. Throughput drops ~60% from 1 thread (~927k) to 8 threads (~388k), with a massive drop happening immediately at 4 threads (-66%). Lock contention and spin loops on a small key pool is a real bottleneck here.


#### Snapshot TxMap
Under contention, the snapshot implementation's performance scales the best compared to the others, though it offers much weaker isolation guarantees
Read-heavy workloads scales well, throughput grows from ~2.0M ops/s at 1 thread to ~2.7M at 8 threads. This is mainly due to the weaker isolation guarantees, allowing writers not to block readers and vice versa
Balanced workloads (50/50) are more interesting, throughput drops ~41% from 1 thread (~1.5M) to 8 threads (~892k). Offering the best scaling for balanced workloads
Write-heavy scales decently as well Throughput drops ~18% from 1 thread (~1.36M) to 8 threads (~1.11M), offering the best write performance out of the three implementations
