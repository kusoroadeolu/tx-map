package io.github.kusoroadeolu.txmap.map;

import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.txmap.FutureValue;
import io.github.kusoroadeolu.txmap.MvccTransactionalMap;
import io.github.kusoroadeolu.txmap.TransactionalMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TransactionalMapTest {
    private MvccTransactionalMap<Object, Object> txMap;

    @BeforeEach
    void setUp() {
        txMap = (MvccTransactionalMap<Object, Object>) TransactionalMap.create();
    }

    // -------------------------------------------------------------------------
    // Basic correctness
    // -------------------------------------------------------------------------

    @Test
    void put_thenGet_returnsNull() {
        try (var tx = txMap.beginTx()) {
            var putFuture = tx.put("foo", 42);
            var getFuture = tx.get("foo");
            tx.commit();

            assertNull(putFuture.get());
            assertNull(getFuture.get());
        }
    }

    @Test
    void remove_existingKey_returnsOldValue() {
        // Seed the map first
        try (var seed = txMap.beginTx()) {
            seed.put("bar", 99);
            seed.commit();
        }

        try (var tx = txMap.beginTx()) {
            var removeFuture = tx.remove("bar");
            var containsFuture = tx.containsKey("bar");
            tx.commit();
            assertEquals(99, removeFuture.get());
            assertEquals(true, containsFuture.get()); //Should return true because we never see writes from our own transactions
        }
    }

    @Test
    void size_reflectsInsertionsAndRemovals() {
        try (var tx = txMap.beginTx()) {
            tx.put("a", 1);
            tx.put("b", 2);
            tx.put("c", 3);
            var sizeFuture = tx.size();
            tx.commit();

            assertEquals(3, sizeFuture.get());
        }

        try (var tx = txMap.beginTx()) {
            tx.remove("a");
            var sizeFuture = tx.size();
            tx.commit();
            assertEquals(2, sizeFuture.get());
            assertNull(txMap.versionChain("a").latest().e());
        }
    }

    @Test
    void put_existingKey_returnsPreviousValue() {
        try (var seed = txMap.beginTx()) {
            seed.put("key", 1);
            seed.commit();
        }

        try (var tx = txMap.beginTx()) {
            var putFuture = tx.put("key", 2);
            tx.commit();

            assertEquals(1, putFuture.get());
        }
    }

    @Test
    void containsKey_missingKey_returnsFalse() {
        try (var tx = txMap.beginTx()) {
            var future = tx.containsKey("ghost");
            tx.commit();
            assertTrue(future.isComplete());
            assertEquals(false, future.get());
        }
    }

    // -------------------------------------------------------------------------
    // Abort behaviour
    // -------------------------------------------------------------------------

    @Test
    void abort_doesNotApplyWrites() {
        try (var tx = txMap.beginTx()) {
            tx.put("aborted", 1);
            tx.abort();
        }

        try (var tx = txMap.beginTx()) {
            var future = tx.containsKey("aborted");
            tx.commit();
            assertEquals(false, future.get());
        }
    }

    @Test
    void abort_futureValuesAreNotCompleted() {
        FutureValue<Object> putFuture;

        try (var tx = txMap.beginTx()) {
            putFuture = tx.put("x", 10);
            tx.abort();
        }

        // Since the tx was aborted, future should not be completed
        assertFalse(putFuture.isComplete());
    }

    @Test
    void autoClose_withoutCommit_abortsTransaction() {
        // try-with-resources should call abort via close()
        try (var tx = txMap.beginTx()) {
            tx.put("autoclosed", 77);
            // No commit — close() should abort
        }



        try (var tx = txMap.beginTx()) {
            var future = tx.containsKey("autoclosed");
            tx.commit();
            assertFalse(future.get());
        }
    }

    // -------------------------------------------------------------------------
    // Isolation
    // -------------------------------------------------------------------------

    @Test
    void uncommittedWrite_notVisibleToOtherTransaction() throws InterruptedException {
        var latch = new CountDownLatch(1);
        var readResult = new Object[1];

        // tx1 writes but doesn't commit yet
        var tx1 = txMap.beginTx();
        tx1.put("shared", 55);

        // tx2 reads concurrently before tx1 commits
        Thread.startVirtualThread(() -> {
            try (var tx2 = txMap.beginTx()) {
                var future = tx2.get("shared");
                tx2.commit();
                readResult[0] = future.get();
            }
            latch.countDown();
        });

        latch.await();
        tx1.commit();
        // tx2 should not have seen the uncommitted value
        assertNull(readResult[0], "Dirty read detected!");
    }


    @Test
    void concurrentWrites_toSameKey_onlyOneWins() throws InterruptedException {
        int threads = 5;
        var executor = Executors.newFixedThreadPool(threads, Thread.ofVirtual().factory());
        var startGate = new CountDownLatch(1);
        var doneGate = new CountDownLatch(threads);
        String cts = "cts";

        for (int i = 0; i < threads; i++) {
            final int val = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    try (var tx = txMap.beginTx()) {
                        tx.put(cts, val);
                        tx.commit();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        executor.close();
        doneGate.await();

        try (var tx = txMap.beginTx()) {
            var containsFuture = tx.containsKey(cts);
            tx.commit();
            assertTrue(containsFuture.get());
        }
    }

    // -------------------------------------------------------------------------
    // Size delta correctness
    // -------------------------------------------------------------------------

    @Test
    void size_multipleTransactions_remainsConsistent() {
        int n = 10;
        for (int i = 0; i < n; i++) {
            try (var tx = txMap.beginTx()) {
                tx.put("key-" + i, i);
                tx.commit();
            }
        }

        try (var tx = txMap.beginTx()) {
            var sizeFuture = tx.size();
            tx.commit();
            assertEquals(n, sizeFuture.get());
        }
    }

}