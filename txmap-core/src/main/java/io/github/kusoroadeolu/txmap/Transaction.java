package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.ferrous.option.Option;

public interface Transaction {
    void commit();
    void abort();
    Option<Transaction> parent();
    TransactionState state();
    default boolean isAborted(){
        return state() == TransactionState.ABORTED;
    }

    default boolean isCommitted(){
        return state() == TransactionState.COMMITTED;
    }
}
