package io.github.kusoroadeolu.txmap;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;

public class BackgroundGCThread<K, V> {
    private final ConcurrentMap<K, VersionChain<V>> map;
    private final Queue<CleanupRequest<K>> cleanupReqs;
    private final Thread.Builder.OfPlatform thread;
    private volatile boolean stop = false;


    public BackgroundGCThread(ConcurrentMap<K, VersionChain<V>> map) {
        this.map = map;
        this.cleanupReqs = new LinkedBlockingQueue<>();
        this.thread = Thread.ofPlatform();
        start();
    }

    public void submitBatchRequest(K key, long tBegin){
        this.cleanupReqs.add(new CleanupRequest<>(key, tBegin));
    }

    private void start(){
        thread.start(() -> {
            while (!stop){
                CleanupRequest<K> current;
                while ((current = cleanupReqs.poll()) != null){
                        map.get(current.key())
                                .removeUnreachableVersions(current.tBegin());


                }
            }
        });
    }

    void stop(){
        this.stop = true;
        this.cleanupReqs.clear();
        this.map.clear();
    }


}
