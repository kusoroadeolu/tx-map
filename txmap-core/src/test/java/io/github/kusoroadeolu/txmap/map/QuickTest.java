package io.github.kusoroadeolu.txmap.map;

import io.github.kusoroadeolu.txmap.Combiner2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class QuickTest {
    @Test
    public void testDefaultLockWrapper() throws InterruptedException {
        Combiner2<List<Integer>> combiner = new Combiner2<>(new ArrayList<>());
        for (int i = 0; i < 170; ++i){
            int j = i;
            Thread.startVirtualThread(() -> combiner.run(list -> list.add(j)));
        }

        Thread.sleep(1000);
        IO.println(combiner.e().size());




    }
}
