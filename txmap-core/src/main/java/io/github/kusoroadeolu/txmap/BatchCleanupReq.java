package io.github.kusoroadeolu.txmap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BatchCleanupReq<K, V> {
        final Set<CleanupRequest<K>> requests;
        static final int BATCH_SIZE = 100; // tune this

        public BatchCleanupReq() {
            this.requests = new HashSet<>(100);
        }

        BatchCleanupReq(Set<CleanupRequest<K>> list) {
            this.requests = list;
        }

        @SuppressWarnings("unchecked")
        void add(CleanupRequest<?> req,  BackgroundGCThread<K, V> cleanupGC) {
            requests.add((CleanupRequest<K>) req);
            if (requests.size() >= BATCH_SIZE) {
                flush(cleanupGC);
            }
        }

        void flush(BackgroundGCThread<K, V> cleanupGC) {
            if (!requests.isEmpty()) {
                cleanupGC.submitBatchRequest(new BatchCleanupReq<>(new HashSet<>(requests)));
                requests.clear();
            }
        }

    }