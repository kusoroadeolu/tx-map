Rethought about what I'm actually benchmarking, I realized that since operations are essentially serialized, trying to measure the effects of read/write heavy uploads per key is basically useless, so I redesigned my benchmarks to measure these instead
1. The change in throughput as the number of threads increased
2. The change in throughput of the number of operations per transaction

Later on I realized my code had a significant flaw, each transaction created its own individual combiner which meant operations weren't fully serialized rendering my inital benchmarks(from my first article) useless, my tests were supposed to catch that no idea how that got through
With these changes and the introduction of my atomic array combiner(which even with the old benchmarks showed tighter variance, compared to unbound and semaphore), I plan to rerun these tests. I also optimized the semaphore combiner to ditch the volatile semantics and use plain writes with strong visibility guarantees but no reordering guarantees

TODO: Investigate, why did a volatile read after the combiner scanned the pub queue/arr, while still holding the lock, fix the issue where threads hung on their condition indefinitely