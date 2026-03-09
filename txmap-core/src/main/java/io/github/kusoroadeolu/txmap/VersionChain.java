package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.txmap.vchain.Version;

public interface VersionChain<E> {
    Version<E> latest();

    E enqueueNewVersion(E e, long beginTs, TransactionID txnId);

    Version<E> findOverlap(long tBegin);

    void removeUnreachableVersions(long tBegin);

    int size();
}
