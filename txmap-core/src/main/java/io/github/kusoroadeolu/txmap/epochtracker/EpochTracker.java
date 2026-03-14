package io.github.kusoroadeolu.txmap.epochtracker;

public interface EpochTracker {
    long newEpoch();

    long currentEpoch();

    void leaveEpoch(long epoch);

    //Find the minimum visible epoch
    long minVisibleEpoch();
}
