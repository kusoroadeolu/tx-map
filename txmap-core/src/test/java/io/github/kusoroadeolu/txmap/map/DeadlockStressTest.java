package io.github.kusoroadeolu.txmap.map;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stress test to check for deadlocks between concurrent PUT and REMOVE
 * operations on a OptimisticTransactionalMap.
 *
 * Strategy:
 *  - Spawn N threads, each repeatedly doing a transaction that mixes put/remove
 *    on a small, overlapping key set (to maximize lock contention).
 *  - Each transaction is submitted to an executor with a hard timeout.
 *  - If any transaction exceeds the timeout, we treat it as a deadlock and fail fast.
 *  - At the end we also do a thread dump so you can see exactly where things hung.
 */
public class DeadlockStressTest {

    // ---- Tuning knobs ----
    static final int NUM_THREADS       = 8;
    static final int OPS_PER_THREAD    = 200;
    static final int KEY_SPACE         = 4;   // small key space = high contention
    static final long TX_TIMEOUT_MS    = 5_000; // if a tx takes longer than this, it's a deadlock
    static final long TEST_TIMEOUT_SEC = 60;

    // ---- Counters ----
    static final AtomicInteger puts      = new AtomicInteger();
    static final AtomicInteger removes   = new AtomicInteger();
    static final AtomicInteger aborts    = new AtomicInteger();
    static final AtomicInteger deadlocks = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Deadlock Stress Test ===");
        System.out.printf("Threads: %d | Ops/thread: %d | Key space: %d | Tx timeout: %dms%n%n",
                NUM_THREADS, OPS_PER_THREAD, KEY_SPACE, TX_TIMEOUT_MS);

        var txMap = new OptimisticTransactionalMap<Integer, String>();
        var executor = Executors.newFixedThreadPool(NUM_THREADS);
        var futures = new ArrayList<Future<?>>();

        long start = System.currentTimeMillis();

        for (int t = 0; t < NUM_THREADS; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> runThread(txMap, threadId)));
        }

        // Wait for all threads, with an overall test timeout
        executor.shutdown();
        boolean finished = executor.awaitTermination(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);

        long elapsed = System.currentTimeMillis() - start;

        System.out.println("=== Results ===");
        System.out.printf("Elapsed  : %dms%n", elapsed);
        System.out.printf("Puts     : %d%n", puts.get());
        System.out.printf("Removes  : %d%n", removes.get());
        System.out.printf("Aborts   : %d%n", aborts.get());
        System.out.printf("Deadlocks: %d%n", deadlocks.get());

        if (!finished) {
            System.out.println("\n!!! TEST TIMED OUT - likely deadlock. Thread dump below:");
            printThreadDump();
            System.exit(1);
        }

        // Check if any individual tx timed out (caught inside runThread)
        for (var f : futures) {
            try {
                f.get();
            } catch (ExecutionException e) {
                System.out.println("\n!!! Thread threw exception: " + e.getCause());
                e.getCause().printStackTrace();
                System.exit(1);
            }
        }

        if (deadlocks.get() > 0) {
            System.out.println("\n!!! DEADLOCKS DETECTED: " + deadlocks.get());
            System.exit(1);
        } else {
            System.out.println("\n✓ No deadlocks detected.");
        }
    }

    static void runThread(OptimisticTransactionalMap<Integer, String> txMap, int threadId) {
        var rng = new Random(threadId * 31L);
        // Use a single-thread executor per transaction so we can apply a timeout to each tx
        var txExecutor = Executors.newSingleThreadExecutor();

        try {
            for (int i = 0; i < OPS_PER_THREAD; i++) {
                int key1 = rng.nextInt(KEY_SPACE);
                int key2 = rng.nextInt(KEY_SPACE);
                boolean doPut = rng.nextBoolean();

                Future<?> txFuture = txExecutor.submit(() -> {
                    var tx = txMap.beginTx();
                    try {
                        if (doPut) {
                            tx.put(key1, "v-" + threadId);
                            tx.remove(key2);  // mixed op in same tx
                            tx.commit();
                            puts.incrementAndGet();
                        } else {
                            tx.remove(key1);
                            tx.put(key2, "v-" + threadId);
                            tx.commit();
                            removes.incrementAndGet();
                        }
                    } catch (Exception e) {
                        try { tx.abort(); } catch (Exception ignored) {}
                        aborts.incrementAndGet();
                    }
                });

                try {
                    txFuture.get(TX_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    deadlocks.incrementAndGet();
                    System.out.printf("!!! TX TIMEOUT on thread %d, op %d (key1=%d key2=%d doPut=%b)%n",
                            threadId, i, key1, key2, doPut);
                    printThreadDump();
                    txFuture.cancel(true);
                    // Fail fast
                    throw new RuntimeException("Deadlock detected on thread " + threadId);
                } catch (ExecutionException | InterruptedException e) {
                    // tx-level exception already handled inside, just continue
                }
            }
        } finally {
            txExecutor.shutdownNow();
        }
    }

    static void printThreadDump() {
        var bean = java.lang.management.ManagementFactory.getThreadMXBean();
        var infos = bean.dumpAllThreads(true, true);
        System.out.println("\n--- Thread Dump ---");
        for (var info : infos) {
            System.out.println(info);
        }

        // Also explicitly check for deadlocked threads
        long[] deadlockedIds = bean.findDeadlockedThreads();
        if (deadlockedIds != null) {
            System.out.println("JVM-detected deadlocked threads:");
            for (var info : bean.getThreadInfo(deadlockedIds, true, true)) {
                System.out.println(info);
            }
        } else {
            System.out.println("JVM ThreadMXBean found no deadlocked threads (may be a livelock or timeout).");
        }
    }
}