module txmap.core {
    requires ferrous;
    requires org.jspecify;
    requires java.management;
    requires java.rmi;
    requires java.xml;
    exports io.github.kusoroadeolu.txmap;
    exports io.github.kusoroadeolu.txmap.txkeeper;
    exports io.github.kusoroadeolu.txmap.vchain;
}