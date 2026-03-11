package io.github.kusoroadeolu.txmap;

public interface Transaction {
    void commit();
    void abort();
    Transaction parent();
    TransactionState state();
}
