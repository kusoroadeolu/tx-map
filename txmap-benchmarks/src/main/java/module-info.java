module txmap.benchmarks {
    requires txmap.core;
    requires jmh.core;
    requires jdk.unsupported;


    exports io.github.kusoroadeolu.txmap.benchmarks.jmh_generated to jmh.core;
    opens io.github.kusoroadeolu.txmap.benchmarks to jmh.core;
    opens io.github.kusoroadeolu.txmap.benchmarks.jmh_generated to jmh.core;
}