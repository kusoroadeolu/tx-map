package io.github.kusoroadeolu.txmap.map;

import io.github.kusoroadeolu.txmap.AtomicArrayCombiner;
import io.github.kusoroadeolu.txmap.Combiner;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Stress test for AtomicArrayCombiner.
 *
 * Tests:
 *  1. Correctness    — all actions are applied exactly once, no lost updates
 *  2. Completeness   — every combine() call eventually returns (no deadlock/livelock)
 *  3. Result visibility — results are correctly returned to the calling thread
 *  4. High contention — hammers the combiner with many threads simultaneously
 */
public class AtomicArrayCombinerStressTest {

    // -------------------------------------------------------------------------
    // Config
    // -------------------------------------------------------------------------
    static final int THREADS        = 16;
    static final int OPS_PER_THREAD = 10_000;
    static final int COMBINER_CAP   = 64;
    static final long TIMEOUT_MS    = 30_000;

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) throws Exception {
        test_correctness_singleThread();
        test_allActionsApplied_multiThread();
        test_resultsReturnedToCorrectThread();
        test_noDeadlock_highContention();
        test_nodeReuse_acrossMultipleCombines();

        System.out.println("\n=============================");
        System.out.printf("  Results: %d passed, %d failed%n", passed, failed);
        System.out.println("=============================");
        if (failed > 0) System.exit(1);
    }

    // -------------------------------------------------------------------------
    // Test 1: Single-threaded sanity check
    // -------------------------------------------------------------------------
    static void test_correctness_singleThread() throws Exception {
        System.out.println("\n[TEST 1] Single-thread correctness...");
        AtomicInteger counter = new AtomicInteger(0);
        AtomicArrayCombiner<AtomicInteger> combiner = new AtomicArrayCombiner<>(counter, COMBINER_CAP);

        int ops = 1000;
        for (int i = 0; i < ops; i++) {
            combiner.combine(c -> { c.incrementAndGet(); return null; });
        }

        if (counter.get() == ops) {
            pass("counter == " + ops);
        } else {
            fail("Expected " + ops + " but got " + counter.get());
        }
    }

    // -------------------------------------------------------------------------
    // Test 2: All actions are applied under multi-threaded contention
    // -------------------------------------------------------------------------
    static void test_allActionsApplied_multiThread() throws Exception {
        System.out.println("\n[TEST 2] All actions applied (multi-thread)...");
        AtomicInteger counter = new AtomicInteger(0);
        AtomicArrayCombiner<AtomicInteger> combiner = new AtomicArrayCombiner<>(counter, COMBINER_CAP);

        runThreads(THREADS, OPS_PER_THREAD, TIMEOUT_MS, () -> {
            combiner.combine(c -> { c.incrementAndGet(); return null; });
        });

        int expected = THREADS * OPS_PER_THREAD;
        if (counter.get() == expected) {
            pass("counter == " + expected);
        } else {
            fail("Expected " + expected + " but got " + counter.get() + " (lost " + (expected - counter.get()) + " updates)");
        }
    }

    // -------------------------------------------------------------------------
    // Test 3: Result is returned to the correct calling thread
    // -------------------------------------------------------------------------
    static void test_resultsReturnedToCorrectThread() throws Exception {
        System.out.println("\n[TEST 3] Results returned to correct thread...");
        AtomicInteger sharedCounter = new AtomicInteger(0);
        AtomicArrayCombiner<AtomicInteger> combiner = new AtomicArrayCombiner<>(sharedCounter, COMBINER_CAP);
        AtomicBoolean mismatch = new AtomicBoolean(false);

        // Each thread increments and checks it got back a non-null result
        runThreads(THREADS, 1000, TIMEOUT_MS, () -> {
            Integer result = combiner.combine(c -> c.incrementAndGet());
            if (result == null) {
                mismatch.set(true);
            }
        });

        if (!mismatch.get()) {
            pass("All threads received non-null results");
        } else {
            fail("Some threads got null results back");
        }
    }

    // -------------------------------------------------------------------------
    // Test 4: No deadlock under high contention (timeout-based)
    // -------------------------------------------------------------------------
    static void test_noDeadlock_highContention() throws Exception {
        System.out.println("\n[TEST 4] No deadlock under high contention...");
        AtomicInteger counter = new AtomicInteger(0);
        // Small capacity to maximize contention
        AtomicArrayCombiner<AtomicInteger> combiner = new AtomicArrayCombiner<>(counter, 4);

        try {
            runThreads(8, 500, 10_000, () -> {
                combiner.combine(c -> { c.incrementAndGet(); return null; });
            });
            pass("Completed without deadlock");
        } catch (TimeoutException e) {
            fail("DEADLOCK or LIVELOCK detected — timed out after 10s");
        }
    }

    // -------------------------------------------------------------------------
    // Test 5: Node reuse across multiple combine() calls on same thread
    // Targets the ThreadLocal node reuse race condition
    // -------------------------------------------------------------------------
    static void test_nodeReuse_acrossMultipleCombines() throws Exception {
        System.out.println("\n[TEST 5] Node reuse across multiple combine() calls...");
        AtomicInteger counter = new AtomicInteger(0);
        Combiner<AtomicInteger> combiner = new AtomicArrayCombiner<>(counter, COMBINER_CAP);
        AtomicBoolean wrongResult = new AtomicBoolean(false);

        // Each thread does many sequential combines and checks results are sane
        runThreads(THREADS, 2000, TIMEOUT_MS, () -> {
            for (int i = 0; i < 5; i++) {
                int val = i;
                Integer result = combiner.combine(c -> {
                    c.incrementAndGet();
                    return val; // return the local val, should match what we passed
                });
                if (result == null || result != val) {
                    wrongResult.set(true);
                }
            }
        });

        if (!wrongResult.get()) {
            pass("Node reuse: all results correct across sequential combines");
        } else {
            fail("Node reuse: a thread got back a wrong/stale result — possible node reuse race!");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    static void runThreads(int threads, int opsPerThread, long timeoutMs, Runnable task)
            throws InterruptedException, TimeoutException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < opsPerThread; i++) task.run();
            }));
        }

        ready.await();
        start.countDown(); // all threads start simultaneously

        pool.shutdown();
        boolean done = pool.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
        if (!done) {
            pool.shutdownNow();
            throw new TimeoutException("Timed out after " + timeoutMs + "ms");
        }

        for (Future<?> f : futures) f.get(); // rethrow any exceptions from threads
    }

    static void pass(String msg) {
        passed++;
        System.out.println("  ✅ PASS: " + msg);
    }

    static void fail(String msg) {
        failed++;
        System.out.println("  ❌ FAIL: " + msg);
    }
}