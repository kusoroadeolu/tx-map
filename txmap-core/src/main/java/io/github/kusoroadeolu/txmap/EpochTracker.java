package io.github.kusoroadeolu.txmap;

public interface EpochTracker {
    long newEpoch();

    long currentEpoch();

    void decrementEpoch(long epoch);

    //Find the minimum active epoch
    long minActiveEpoch();
}
