package io.github.kusoroadeolu.txmap;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public class BackgroundGCThread<K, V> {
    private final ConcurrentMap<K, VersionChain<V>> map;
    private final Queue<BatchCleanupReq<K, V>> batchCleanupReqs;
    private final Thread.Builder.OfVirtual thread;
    private volatile boolean stop = false;


    public BackgroundGCThread(ConcurrentMap<K, VersionChain<V>> map) {
        this.map = map;
        this.batchCleanupReqs = new ConcurrentLinkedQueue<>();
        this.thread = Thread.ofVirtual();
        start();
    }

    public void submitBatchRequest(BatchCleanupReq<K, V> req){
        this.batchCleanupReqs.add(req);
    }

    private void start(){
        thread.start(() -> {
            while (!stop){
                BatchCleanupReq<K, V> current;
                while ((current = batchCleanupReqs.poll()) != null){
                    for (CleanupRequest<K> request : current.requests){
                        map.get(request.key())
                                .removeUnreachableVersions(request.tBegin());
                    }

                }
            }
        });
    }

    void stop(){
        this.stop = true;
        this.batchCleanupReqs.clear();
        this.map.clear();
    }


}
