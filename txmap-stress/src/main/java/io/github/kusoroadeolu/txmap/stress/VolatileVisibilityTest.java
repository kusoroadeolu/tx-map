package io.github.kusoroadeolu.txmap.stress;

import org.openjdk.jcstress.annotations.*;

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

}