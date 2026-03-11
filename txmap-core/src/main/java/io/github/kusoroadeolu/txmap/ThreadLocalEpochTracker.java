package io.github.kusoroadeolu.txmap;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

//This performs best when paired with pooled platform threads, if this is paired with multiple virtual threads, the results might be less desirable than @see DefaultEpochTracker
public class ThreadLocalEpochTracker implements EpochTracker{
    private static final long NO_ACTIVE_TXN = -1;
    private final AtomicLong currentEpoch;
    private final ConcurrentMap<Long, Long> activeTxs; //Thread ID to their current epoch

    public ThreadLocalEpochTracker() {
        this.currentEpoch = new AtomicLong();
        this.activeTxs = new ConcurrentHashMap<>();
    }

    //Commit No
    public long newEpoch(){
        return currentEpoch.incrementAndGet();
    }


    //TBegin
    public long currentEpoch(){
        long id = Thread.currentThread().threadId();
        long epoch = currentEpoch.get();
        activeTxs.put(id, epoch);
        return epoch;
    }


    //
    @Override
    public void leaveEpoch(long epoch){
        long id = Thread.currentThread().threadId();
        activeTxs.put(id,NO_ACTIVE_TXN); //Place-holder meaning, we don't current have an active transaction
    }

    //Find the minimum active epoch
    @Override
    public long minActiveEpoch(){
        return activeTxs.values().stream()
                .filter(l -> l != NO_ACTIVE_TXN)
                .min(Long::compare)
                .orElse(currentEpoch.get());
    }

}
