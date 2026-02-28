package io.github.kusoroadeolu.txmap.stress;

import io.github.kusoroadeolu.txmap.Combiner;
import io.github.kusoroadeolu.txmap.UnboundCombiner;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.II_Result;

@JCStressTest
@Outcome(id = "1, 2", expect = Expect.ACCEPTABLE, desc = "Should either be one or two since the combiner is fully serialized")
@Outcome(id = "2, 1", expect = Expect.ACCEPTABLE, desc = "Should either be two or one since the combiner is fully serialized")
@Outcome(expect = Expect.FORBIDDEN, desc = "Cannot be one or one, two or two")
@State
public class LinearUpdateStressTest {
    Combiner<IntAdder> combiner = new UnboundCombiner<>(new IntAdder());

    @Actor
    public void actor1(II_Result r) {
        r.r1 = combiner.combine(IntAdder::incrementAndGet);
    }

    @Actor
    public void actor2(II_Result r) {
        r.r2 = combiner.combine(IntAdder::incrementAndGet);
    }

    static class IntAdder {
        int a;

        public int incrementAndGet(){
            return ++a;
        }
    }
}