package io.github.kusoroadeolu.txmap.map;


import io.github.kusoroadeolu.txmap.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;

public class QuickTest {
    @Test
    public void testComb() throws InterruptedException {
        Combiner<List<Integer>> unboundCombiner = new AtomicArrayCombiner<>(new ArrayList<>());
        for (int i = 0; i < 170; ++i){
            int j = i;
            Thread.startVirtualThread(() -> unboundCombiner.combine(list -> list.add(j)));
        }

        Thread.sleep(2000);
        IO.println(unboundCombiner.e().size());
    }

    @Test
    public void testComb2() throws InterruptedException {
        Combiner<List<Integer>> unboundCombiner = new AtomicArrayCombiner<>(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(100);
        AtomicInteger hanging = new AtomicInteger(200);
        for (int i = 0; i < 200; ++i){
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
        IO.println("SIZE:" + unboundCombiner.e().size());

    }


}
