package io.github.kusoroadeolu.txcoll;

public interface Transaction {
    void commit();
    void abort();
}
