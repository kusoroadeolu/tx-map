package io.github.kusoroadeolu.txmap.epochtracker;

import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;

import java.util.concurrent.atomic.AtomicLong;

public class Long2LongEpochTracker implements ThreadLocalEpochTracker{

    private static final long NO_ACTIVE_TXN = -1;
    private final AtomicLong currentEpoch;
    private final Long2LongMap activeTxs;

    public Long2LongEpochTracker() {
        this.currentEpoch = new AtomicLong();
        this.activeTxs = Long2LongMaps.synchronize(new Long2LongOpenHashMap());
    }

    public long newEpoch() {
        return currentEpoch.incrementAndGet();
    }

    public long currentEpoch() {
        long id = Thread.currentThread().threadId();
        long epoch = currentEpoch.get();
        activeTxs.put(id, epoch);
        return epoch;
    }

    @Override
    public void leaveEpoch(long epoch) {
        long id = Thread.currentThread().threadId();
        activeTxs.put(id, NO_ACTIVE_TXN);
    }

    @Override
    public long minVisibleEpoch() {
        long min = currentEpoch.get();
        for (long value : activeTxs.values()) {
            if (value != NO_ACTIVE_TXN && value < min) {
                min = value;
            }
        }
        return min;
    }
}

