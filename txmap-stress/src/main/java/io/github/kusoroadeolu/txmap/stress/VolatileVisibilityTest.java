package io.github.kusoroadeolu.txmap.stress;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.II_Result;

import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE_INTERESTING;


public class VolatileVisibilityTest {
    @JCStressTest(Mode.Termination)
    @Outcome(id = "TERMINATED", expect = ACCEPTABLE,             desc = "Gracefully finished")
    @Outcome(id = "STALE",      expect = ACCEPTABLE_INTERESTING, desc = "Test is stuck")
    @State
    public static class TestPlainSpin{
        boolean ready;

        @Actor
        public void actor1() {
            while (!ready); // spin
        }

        @Signal
        public void signal() {
            ready = true;
        }
    }

    @JCStressTest(Mode.Termination)
    @Outcome(id = "TERMINATED", expect = ACCEPTABLE,             desc = "Gracefully finished")
    @Outcome(id = "STALE",      expect = ACCEPTABLE_INTERESTING, desc = "Test is stuck")
    @State
    public static class TestVolatileSpin{
        volatile boolean ready;

        @Actor
        public void actor1() {
            while (!ready); // spin
        }

        @Signal
        public void signal() {
            ready = true;
        }
    }

    @JCStressTest(Mode.Termination)
    @Outcome(id = "TERMINATED", expect = ACCEPTABLE,             desc = "Gracefully finished")
    @Outcome(id = "STALE",      expect = ACCEPTABLE_INTERESTING, desc = "Test is stuck")
    @State
    public static class TestPlainFieldMadeVisibleByVolatileWrite{
        volatile boolean ready;
        int status = 0;

        @Actor
        public void actor1() {
            while (status == 0 && !ready); // spin
        }

        @Signal
        public void signal() {
            status = 1; //Should be eventually visible due to volatile write
            ready = true;
        }
    }

    @JCStressTest(Mode.Termination)
    @Outcome(id = "TERMINATED", expect = ACCEPTABLE, desc = "Finished")
    @Outcome(id = "STALE", expect = ACCEPTABLE_INTERESTING, desc = "Hanging")
    @State
    public static class TestLazySetVisibility{
        private final AtomicInteger integer = new AtomicInteger(0);

        @Actor
        public void actor(){
            while (integer.get() == 0);
        }

        @Signal
        public void signal(){
            integer.lazySet(1);
        }
    }

    //This is a stress test to see if lazy set allows values after it to be reordered before it. Lazy set uses a set release fence to ensure no writes before it are reorder with writes after it, but let's see if writes after can be reordered before it
    @JCStressTest
    @Outcome(id = {"1, 2", "-1, 2", "1, 0", "-1, 0"}, expect = ACCEPTABLE, desc = "Properly Ordered")
    @Outcome(expect = ACCEPTABLE_INTERESTING, desc = "Reordered by compiler")
    @State
    public static class TestLazySetReordering{
        private final AtomicInteger integer = new AtomicInteger(-1);  /*
            tt, tf, ft, ff
            12, 10, -12, -10
        */
        int num;

        @Actor
        public void actor(){
            integer.lazySet(1);
            num = 2;
        }

        @Actor
        public void viewer(II_Result res){
            res.r1 = integer.get();
            res.r2 = num;
        }

    }

}