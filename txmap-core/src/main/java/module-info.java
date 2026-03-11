module txmap.core {
    requires ferrous;
    requires org.jspecify;
    requires java.management;
    requires java.rmi;
    requires java.xml;
    requires it.unimi.dsi.fastutil.core;
    exports io.github.kusoroadeolu.txmap;
    exports io.github.kusoroadeolu.txmap.txkeeper;
    exports io.github.kusoroadeolu.txmap.vchain;
}