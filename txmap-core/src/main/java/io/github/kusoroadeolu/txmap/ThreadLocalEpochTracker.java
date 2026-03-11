package io.github.kusoroadeolu.txmap;//package io.github.kusoroadeolu.txmap;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

//This performs best when paired with pooled platform threads, if this is paired with multiple virtual threads, the results might be less desirable than @see DefaultEpochTracker
public class ThreadLocalEpochTracker implements EpochTracker{
    private static final long[] NO_ACTIVE_TXN = new long[]{-1};
    private final AtomicLong currentEpoch;
    private final ConcurrentMap<Long, long[]> activeTxs; //Thread ID to their current epoch

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
        activeTxs.put(id, new long[]{epoch});
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
                .map(longs -> longs[0])
                .min(Long::compare)
                .orElse(currentEpoch.get());
    }

}

//import it.unimi.dsi.fastutil.longs.Long2LongMaps;
//import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
//import it.unimi.dsi.fastutil.longs.Long2LongMap;
//
//import java.util.concurrent.atomic.AtomicLong;
//
//public class ThreadLocalEpochTracker implements EpochTracker {
//    private static final long NO_ACTIVE_TXN = -1;
//    private final AtomicLong currentEpoch;
//    private final Long2LongMap activeTxs;
//
//    public ThreadLocalEpochTracker() {
//        this.currentEpoch = new AtomicLong();
//        this.activeTxs = Long2LongMaps.synchronize(new Long2LongOpenHashMap());
//        this.activeTxs.defaultReturnValue(NO_ACTIVE_TXN);
//    }
//
//    public long newEpoch() {
//        return currentEpoch.incrementAndGet();
//    }
//
//    public long currentEpoch() {
//        long id = Thread.currentThread().threadId();
//        long epoch = currentEpoch.get();
//        activeTxs.put(id, epoch);
//        return epoch;
//    }
//
//    @Override
//    public void leaveEpoch(long epoch) {
//        long id = Thread.currentThread().threadId();
//        activeTxs.put(id, NO_ACTIVE_TXN);
//    }
//
//    @Override
//    public long minActiveEpoch() {
//        long min = currentEpoch.get();
//        for (long value : activeTxs.values()) {
//            if (value != NO_ACTIVE_TXN && value < min) {
//                min = value;
//            }
//        }
//        return min;
//    }
//}