# MVCC 
The goal of mvcc is to maximize parallelism w/o sacrificing serializability.
Unlike normal DBMS which use row level granularity to manage versions we ofc will be using key level granularity(i.e. versioning on keys)


## Some notes on impl
1. We need a concurrent queue for storing versions(we can use concurrent linked queue from the JDK or we can make our own lightweight queue)
2. We need a way to clean up versions. To do this we can have a worker thread per key, the thread scans x amount of times and always cleans from the heads tail downwards(the thread should never clean up the current version) i.e. Background vacuuming
Also we could make this per transaction when a transaction is in its write phase, after it has enqueued its own version, per-say after the queue reaches a certain size, we could scan the queue and clean up expired/old versions i.e. Cooperative cleaning

## Phases
Read phase -> Read operations look for a visible version depending on begin ts and end ts, update/write operations acquire the write lock and create a new version with the updated value, however we dont enqueue the version yet, also, the version must be by a committed tx
Validation phase -> We assign a serialization timestamp(`Tcommit`) to the transaction, we then check if a transaction that has committed has modified the value we read(for read transactions, if so, we abort releasing all write locks)
Write phase -> Write operations enqueue their versions(begins ts = Tcommit, endts = infinity, also, the previous version's end ts is updated to the current versions begin ts, at this phase the transaction is known as committed, then release their locks
Also, if a version doesnt update the value of the old version, a new version isn't created, to prevent unnecessary aborts


## Probable race conditions
A writer enqueues a new version right as a reader has finished reading(though this might not matter)