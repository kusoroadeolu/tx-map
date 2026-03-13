package io.github.kusoroadeolu.txmap.epochtracker;//package io.github.kusoroadeolu.txmap;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

//This should perform best when paired with pooled platform threads, if this is paired with multiple virtual threads, the results might be less desirable than @see DefaultEpochTracker
public class LongToArrayEpochTracker implements ThreadLocalEpochTracker {
    private static final long NO_ACTIVE_TXN = -1;
    private final AtomicLong currentEpoch;
    private final ConcurrentMap<Long, long[]> activeTxs; //Thread ID to their current epoch

    public LongToArrayEpochTracker() {
        this.currentEpoch = new AtomicLong();
        this.activeTxs = new ConcurrentHashMap<>();
    }

    //Commit No
    public long newEpoch(){
        return currentEpoch.incrementAndGet();
    }


    //each key will only be accessed by one thread at most, at a time
    public long currentEpoch(){
        long id = Thread.currentThread().threadId();
        long epoch = currentEpoch.get();
        activeTxs.computeIfAbsent(id, _ -> new long[1])[0] = epoch;
        return epoch;
    }


    //
    @Override
    public void leaveEpoch(long epoch){
        long id = Thread.currentThread().threadId();
        activeTxs.get(id)[0] = NO_ACTIVE_TXN; //Place-holder meaning, we don't current have an active transaction
    }

    //Find the minimum active epoch
    @Override
    public long minActiveEpoch(){
        return activeTxs.values().stream()
                .map(arr -> arr[0])
                .filter(l -> l != NO_ACTIVE_TXN)
                .min(Long::compare)
                .orElse(currentEpoch.get());
    }

}

