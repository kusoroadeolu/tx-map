package io.github.kusoroadeolu.txmap.stress;

import io.github.kusoroadeolu.txmap.AtomicArrayCombiner;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.II_Result;

import javax.xml.crypto.NodeSetData;
import java.util.concurrent.atomic.AtomicInteger;

import static org.openjdk.jcstress.annotations.Expect.*;


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

    @JCStressTest
    @Outcome(id = "1, 42", expect = ACCEPTABLE)
    @Outcome(id = "0, 0", expect = ACCEPTABLE)
    @Outcome(id = "1, 0", expect = FORBIDDEN) // saw isApplied but not result!
    @Outcome(id = "0, 42", expect = ACCEPTABLE_INTERESTING)
    @State
    public static class ApplyVisibilityTest {

        AtomicArrayCombiner.Node<Integer, Integer> node = new AtomicArrayCombiner.Node<>(a -> a);

        @Actor
        public void writer() {
            node.stateful.apply(42); // writes result then isApplied
        }

        @Actor
        public void reader(II_Result r) {
            r.r1 = node.stateful.isApplied ? 1 : 0;
            Integer res = node.stateful.result;
            r.r2 = res == null ? 0 : res; // is this visible when isApplied is true?
        }

    }
}