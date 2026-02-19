package io.github.kusoroadeolu.txcoll.handlers;

public interface AbortHandler {
    void abortByConflictingSemantic();
    void abort();
}
