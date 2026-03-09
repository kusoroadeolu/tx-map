package io.github.kusoroadeolu.txmap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultEpochTracker implements EpochTracker{
    private final AtomicLong currentEpoch;
    private final ConcurrentHashMap<Long, AtomicLong> activeTxs;

    public DefaultEpochTracker() {
        this.currentEpoch = new AtomicLong();
        this.activeTxs = new ConcurrentHashMap<>();
    }

    @Override
    //Commit No
    public long newEpoch(){
        return currentEpoch.incrementAndGet();
    }


    //TBegin
    public long currentEpoch(){
        long epoch = currentEpoch.get();
        var al = activeTxs.get(epoch);
        al = activeTxs.computeIfAbsent(epoch, _ -> new AtomicLong());
        al.incrementAndGet();
        return epoch;
    }

    @Override
    public void decrementEpoch(long epoch){
        var al = activeTxs.get(epoch);
        if (al != null && al.decrementAndGet() == 0){
            activeTxs.remove(epoch);
        }
    }

    //Find the minimum active epoch
    @Override
    public long minActiveEpoch(){
//        return activeTxs.isEmpty()
//                ? currentEpoch.get()
//                : activeTxs.firstKey();
        return activeTxs.keySet().stream().min(Long::compare).orElse(currentEpoch.get());
    }

}
