package io.github.kusoroadeolu.txmap.map;


import io.github.kusoroadeolu.txmap.Combiner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

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
        CountDownLatch latch = new CountDownLatch(2);
        Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 5; ++i){
                int j = i;
                 combiner.combine( list -> list.add(j));
            }
            latch.countDown();
        });

        Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 5; ++i){
                int j = i;
                combiner.combine( list -> list.add(j));
            }
            latch.countDown();
        });


        latch.await();
        IO.println(combiner.e().size());

    }
}
