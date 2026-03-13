package io.github.kusoroadeolu.txmap.stress;

import io.github.kusoroadeolu.txmap.MvccTransactionalMap;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.II_Result;
import org.openjdk.jcstress.infra.results.I_Result;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;



public class MvccStress {
    @JCStressTest
    @Outcome(id = {"-1", "0", "2"}, expect = ACCEPTABLE)
    @State
    public static class ReaderStress {
        public MvccTransactionalMap<String, Integer> map;
        volatile int res;

        public ReaderStress() {
            this.map = new MvccTransactionalMap<>();
        }


        //Valid results
        // 0, -1, 2
        //  java -jar jcstress.jar -t ClassName.StaticClass(ifPresent) -v
        @Actor
        public void writer() {

            try (var tx = map.beginTx()) {
                tx.put("1", 2);
                tx.commit();
            }

        }


        //Reading actor
        @Actor
        public void reader() {


            try (var tx = map.beginTx()) {
                var futureValue = tx.get("1");
                tx.commit();

                if (tx.isAborted()) res = -1;
                else {
                    var val = futureValue.get();
                    res = val == null ? 0 : val;
                }
            }
        }

        @Arbiter
        public void arbiter(I_Result r) {
            r.r1 = res;
        }
    }

}
