package io.github.kusoroadeolu.txmap.map;

import io.github.kusoroadeolu.txmap.Combiner;
import io.github.kusoroadeolu.txmap.SemaphoreCombiner;
import io.github.kusoroadeolu.txmap.TestCombiner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class QuickTest {
    @Test
    public void testDefaultLockWrapper() throws InterruptedException {
        SemaphoreCombiner<List<Integer>> combiner = new SemaphoreCombiner<>(new ArrayList<>());
        for (int i = 0; i < 170; ++i){
            int j = i;
            Thread.startVirtualThread(() -> combiner.combine( list -> list.add(j)));
        }

        Thread.sleep(2000);
        IO.println(combiner.e().size());




    }
}
