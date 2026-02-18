# Goals of the paper
Transactional collection classes that allow programmers to
compose multiple operations on transactional objects atomically — something unattainable with undisciplined use of
isolation-reducing mechanisms such as open nesting.
- We show how selectively reducing isolation can yield higher performing data structures by creating a transactional work queue from the Queue class.
- We provide an overview of the transactional memory semantics needed to support the construction of collection classes in either hardware or software systems.
- We summarize design principles about managing state with semantic concurrency control for the case when full isolation and
serializability is desired. We also discuss alternative implementation strategies that we did not explore that may be more appropriate for other transactional memory implementations.


# Transactions
The paper talks about transaction semantics used in db systems. The most common being two phase locking

## Two phase locking
This type of semantic includes two phases
1. Growing: This is the phase when all locks are acquired, then the transaction operations happen. If any individual operation fails everything is rolled back
2. Shrinking: This phase includes the release of all locks sequentially

This semantic emphasizes correctness over concurrency. Acquiring the needed locks for a specific transaction and holding them till the transaction succeeds can hurt performance. This becomes a bigger issue, when an operation in the transaction fails. This leads to wasted work and time spent holding locks, leading to worse perf.


## Closed Nested Transactions
A transaction type in which operations in a transaction are treated as child transactions. Each transaction commits to the parent, rather than the data structure itself. The parent can then handle failed child transactions accordingly

## Open Nested Transactions
A transaction type in which sub operations are treated as child transactions. Each transaction commits to the data structure, rather than the parent itself. This could cause issues in which another transaction could see stale data from an uncommited transaction.

# A General approach to these problems
## Semantic concurrency control
A technique that increases the concurrency of db operations by using semantic/operational meaning rather than read write conflicts.
For example given a HashMap and we want to perform a transaction on it. We incorporate the ideas of nested transactions. In the sense that operations are treated as nested transactions that handle their own commit phase.
</br> To generalize things for all data structures the paper proposes using multilevel transactions. 

- Low level open nested transaction - each child transaction commits to a local store buffer containing the child transaction's semantic meaning and well the value the transaction planned to modify/read and obtaining its corresponding read/write lock'
- High level transactions - when the parent transaction eventually commits, it runs using a commit handler, this handler uses the grabbed locks to enforce semantic concurrent control, and runs an abort handler 
```java
//Given a transaction for a hashmap
Map
TxCollection<Integer, String> map = new TransactionalMap<>();
try(var tx = map.beginTx()){
    tx.put(1, "2");
    tx.get(2);
} //Commits here
/*
 * At commit time, for writes, we look for transactions who have semantic read operations for the key '1' and we use an abort handler to abort that specific child transaction 
 * We then use a commit handler to make these writes visible to users
 * */
```

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
### Shared Fields needed
KeyLockers -> These are transaction operating on a specific key, regardless of its semantics
SizeLockers -> These are transaction which directly affect the size of the collection

### Fields per parent transaction
delta -> change in size of a transaction 
set key locks -> Set of locks held by the transaction //Though this might not be necessary
store buffer -> storing the transaction's semantics

### Fields per child transaction
operation semantics with values if needed -> put, get , remove, update, size, contains key(no contains value for now)
current status -> NONE, COMMITTED, ABORTED


## Transaction flow
1. All read operations in a parent transaction
2. At commit time, all transactions go through 2 processes, validation and modification
3. During validation, each transaction finds conflicting semantic transactions in the key lockers or size lockers collections(though acquires the collection's locks first), aborts all those transactions, then commits itself
</br> If any transaction in those collections is already commited, the transaction aborts itself then releases the lock, after a short duration then retries
4. During validation, if the transaction is already aborted, the transaction, automatically retries itself
5. Once all child transactions in a parent transaction are validated, the transaction commits to the underlying map, then removes its child transactions from the keylockers and sizelockers and then clears its buffer


