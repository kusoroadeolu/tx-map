package io.github.kusoroadeolu.txmap.gc;

import io.github.kusoroadeolu.txmap.epochtracker.EpochTracker;
import io.github.kusoroadeolu.txmap.vchain.VersionChain;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class GCThread<K, V> {
    private final ConcurrentMap<K, VersionChain<V>> map;
    private final Queue<K> cleanupReqs;
    private final Thread.Builder.OfPlatform thread;
    private volatile boolean stop = false;
    private final ScheduledExecutorService scheduler;
    private final MinEpoch epoch;
    private final EpochTracker tracker;


    public GCThread(ConcurrentMap<K, VersionChain<V>> map, EpochTracker epochTracker) {
        this.map = map;
        this.tracker = epochTracker;
        this.epoch = new MinEpoch();
        this.scheduler = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
        this.cleanupReqs = new LinkedBlockingQueue<>(); //Here for backpressure, could become a bottleneck later on, but yeah this is alr for now
        this.thread = Thread.ofPlatform().daemon().name("worker-gc-thread");
        start();
    }

    public void submitCleanupRequest(K key){
        this.cleanupReqs.add(key);
    }

    private void start(){
        scheduler.scheduleAtFixedRate(() -> this.epoch.setEpoch(tracker.minVisibleEpoch()), 0, 100 ,TimeUnit.MILLISECONDS);
        thread.start(() -> {
            while (!stop){
                K current;
                while ((current = cleanupReqs.poll()) != null){
                        map.get(current).pruneUnreachableVersions(epoch.getCurrentEpoch());
                }
            }
        });
    }

    public void stop(){
        this.stop = true;
        this.cleanupReqs.clear();
        this.map.clear();
    }

    static class MinEpoch{
         private final AtomicLong epoch;

        public MinEpoch() {
            this.epoch = new AtomicLong();
        }

        void setEpoch(long current){
            epoch.set(current);
        }

        long getCurrentEpoch(){
            return epoch.get();
        }
    }
}
