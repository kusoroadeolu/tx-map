package io.github.kusoroadeolu.txmap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class EpochTracker {
    private final AtomicLong currentEpoch;
    private final ConcurrentMap<Long, AtomicLong> activeTxs;

    public EpochTracker() {
        this.currentEpoch = new AtomicLong();
        this.activeTxs = new ConcurrentHashMap<>();
    }

    public long newCommitNo(){
        return currentEpoch.incrementAndGet();
    }

    public long currentEpoch(){
        long epoch = currentEpoch.get();
        var al = activeTxs.get(epoch);
        if (al == null) al = activeTxs.computeIfAbsent(epoch, _ -> new AtomicLong());
        al.incrementAndGet();
        return epoch;
    }

    public void decrementEpoch(long epoch){
        var al = activeTxs.get(epoch);
        if (al != null && al.decrementAndGet() == 0){
            activeTxs.remove(epoch);
        }
    }

    //Find the minimum active epoch
    public long minActiveTBegin(){
        return activeTxs.keySet().stream()
                .min(Long::compare)
                .orElse(currentEpoch.get());
    }


}
