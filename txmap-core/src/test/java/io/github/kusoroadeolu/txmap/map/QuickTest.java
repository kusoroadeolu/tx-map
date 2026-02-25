package io.github.kusoroadeolu.txmap.map;


import io.github.kusoroadeolu.txmap.FlatCombinedTxMap;
import io.github.kusoroadeolu.txmap.MapTransaction;
import io.github.kusoroadeolu.txmap.UnboundCombiner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class QuickTest {
    @Test
    public void testComb() throws InterruptedException {
        UnboundCombiner<List<Integer>> unboundCombiner = new UnboundCombiner<>(new ArrayList<>());
        for (int i = 0; i < 170; ++i){
            int j = i;
            Thread.startVirtualThread(() -> unboundCombiner.combine(list -> list.add(j)));
        }

        Thread.sleep(2000);
        IO.println(unboundCombiner.e().size());
    }

    @Test
    public void testComb2() throws InterruptedException {
        UnboundCombiner<List<Integer>> unboundCombiner = new UnboundCombiner<>(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(100);
        AtomicInteger hanging = new AtomicInteger(100);
        for (int i = 0; i < 100; ++i){
            int j = i;
            Thread.ofPlatform().name(i+"").start(() -> {
                   try {
                       start.await();
                       unboundCombiner.combine(l -> l.add(j));
                   } catch (InterruptedException e) {
                       throw new RuntimeException(e);
                   }finally {
                       done.countDown();
                       IO.println("%s hanging threads".formatted(hanging.decrementAndGet()));
                   }

            });
        }
        start.countDown();
        done.await();
        IO.println(unboundCombiner.e().size());

    }

    @Test
    public void testConcMap(){
        FlatCombinedTxMap<String, Integer> map = new FlatCombinedTxMap<>();
        try(var tx = map.beginTx()) {
            tx.put("Hello", 2);
            tx.put("Hii", 3);
            tx.commit();
        }

        IO.println(map.map().get("Hii"));
    }

}
