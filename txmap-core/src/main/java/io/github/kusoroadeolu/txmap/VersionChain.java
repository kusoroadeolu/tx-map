package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.txmap.vchain.Version;

public interface VersionChain<E> {
    Version<E> latest();

    E addNewVersion(E e, long beginTs, TransactionID txnId);

    Version<E> findOverlap(long tBegin);

    void pruneUnreachableVersions(long from);

    int size();
}
