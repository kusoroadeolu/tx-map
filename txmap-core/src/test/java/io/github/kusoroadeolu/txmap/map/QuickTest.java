package io.github.kusoroadeolu.txmap.map;


import io.github.kusoroadeolu.txmap.Combiner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class QuickTest {
    @Test
    public void testComb() throws InterruptedException {
        Combiner<List<Integer>> combiner = new Combiner<>(new ArrayList<>());
        for (int i = 0; i < 170; ++i){
            int j = i;
            Thread.startVirtualThread(() -> combiner.combine( list -> list.add(j)));
        }

        Thread.sleep(2000);
        IO.println(combiner.e().size());
    }

    @Test
    public void testComb2() throws InterruptedException {
        Combiner<List<Integer>> combiner = new Combiner<>(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(100);
        AtomicInteger hanging = new AtomicInteger(100);
        for (int i = 0; i < 100; ++i){
            int j = i;
            Thread.ofPlatform().name(i+"").start(() -> {
                   try {
                       start.await();
                       combiner.combine(l -> l.add(j));
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
        IO.println(combiner.e().size());

    }
}
