package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.txmap.txkeeper.VersionChainType;
import io.github.kusoroadeolu.txmap.vchain.NavigableVersionChain;
import io.github.kusoroadeolu.txmap.vchain.QueueVersionChain;
import io.github.kusoroadeolu.txmap.vchain.Version;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.kusoroadeolu.txmap.MvccTransactionalMap.MvccTx.WriteOperation.WriteType.PUT;
import static io.github.kusoroadeolu.txmap.MvccTransactionalMap.MvccTx.WriteOperation.WriteType.REMOVE;


//Append only storage
//For garbage collection, the issue is knowing when a version is not visible to other transactions
// version.begin-ts <= tBegin < version.end-ts
//TODO, write heavy workloads have crazy error margins, my current suspect is the garbage collection running on the write transactions thread, clearing unreachable versions after every N iterations could cause issues, cause in a queue of 500k, half of those versions might still be reachable, so we're basically going to iterate this on every write tx that acquires the lock
public class MvccTransactionalMap<K, V> implements TransactionalMap<K, V>{
    private final EpochTracker epochTracker; //Incremented at commit time
    private final ConcurrentMap<K, VersionChain<V>> underlying;
    private final ConcurrentMap<K, KeyStatus> status; //Keeping the status to the key
    private final TransactionIDGenerator idGenerator;
    private final GCThread<K, V> gcThread;
    private final int versionThreshold;
    private final VersionChainType versionChainType;
    private final AtomicInteger size;

    public MvccTransactionalMap() {
        this(100);
    }

    public MvccTransactionalMap(int threshold) {
        this(threshold, VersionChainType.QUEUE);
    }

    public MvccTransactionalMap(VersionChainType type) {
        this(100, type);
    }

    public MvccTransactionalMap(int threshold, VersionChainType versionChainType) {
        this.epochTracker = new ThreadLocalEpochTracker();
        this.status = new ConcurrentHashMap<>();
        this.underlying = new ConcurrentHashMap<>();
        this.idGenerator = new TransactionIDGenerator();
        this.gcThread = new GCThread<>(underlying, epochTracker);
        this.size = new AtomicInteger();
        this.versionThreshold = threshold;
        this.versionChainType = versionChainType;
    }

    KeyStatus keyStatus(K key){
        var ksMap = status;
        KeyStatus ks = ksMap.get(key);
        if (ks == null) {
           ks = ksMap.computeIfAbsent(key, _ -> new KeyStatus());
        }

        return ks;
    }

    public VersionChain<V> versionChain(K key){
        var versionChain = underlying.get(key);
        if(versionChain == null) {
           versionChain = switch (versionChainType){
               case QUEUE -> new QueueVersionChain<>();
               case NAVIGABLE -> new NavigableVersionChain<>();
           };
        }
        return versionChain;
    }

    @Override
    public void stop() {
        gcThread.stop();
    }

    @Override
    public MapTransaction<K, V> beginTx() {
        return new MvccTx<>(this);
    }

    static class MvccTx<K, V> implements MapTransaction<K, V>{
        private final MvccTransactionalMap<K, V> map;
        private final TransactionID txnId; //The transaction id
        private final long tBegin; // The current txcommit number at the transaction start time
        private long tCommit; //Txcommit number assigned at validation time
        private final List<WriteOperation<K, V>> writeSet;
        private final List<ReadOperation<K, Object>> readSet;
        private final AtomicInteger size;
        private TransactionState state = TransactionState.IN_PROGRESS;


        public MvccTx(MvccTransactionalMap<K, V> map) {
            this.map = map;
            this.txnId = new TransactionID(map.idGenerator.newId());
            this.tBegin = map.epochTracker.currentEpoch();
            this.readSet = new ArrayList<>(4);
            this.writeSet = new ArrayList<>(4);
            this.size = map.size;
        }

        @Override
        public FutureValue<V> put(K key, V value) {
            return this.doWrite(key, value, PUT);
        }

        @Override
        public FutureValue<V> remove(K key) {
            return this.doWrite(key, null, REMOVE);
        }



        FutureValue<V> doWrite(K key, V value, WriteOperation.WriteType type){
            if (isAborted()) {
                return uncompletedFuture();
            }

            var ks = map.keyStatus(key);
            boolean alreadyHeld = this.tryHold(ks); //If we fail to hold the 'lock' and the lock holder hasnt committed just abort the whole tx
            if (alreadyHeld) { //Failed to hold write lock, abort
                this.setAborted();
                return uncompletedFuture();
            }

            WriteOperation<K, V> wo = new WriteOperation<>(key, value, this, type);
            writeSet.add(wo);

            VersionChain<V> versionChain = map.versionChain(key);
            Version<V> overlap = versionChain.findOverlap(tBegin);
            if (!Objects.equals(overlap, versionChain.latest())) { //Stale write version, abort
                this.setAborted();
                return uncompletedFuture();
            }

            return wo.future;
        }

        @SuppressWarnings("unchecked")
        @Override
        public FutureValue<V> get(K key) {
            if (isAborted()) return uncompletedFuture();
            FutureValue<?> future = this.doRead(key, ReadOperation.ReadType.GET);
            return (FutureValue<V>) future;
        }

        @SuppressWarnings("unchecked")
        @Override
        public FutureValue<Boolean> containsKey(K key) {
            if (isAborted()) return (FutureValue<Boolean>) FutureValue.uncompletedFuture();
            FutureValue<?> future = this.doRead(key, ReadOperation.ReadType.CONTAINS);
            return (FutureValue<Boolean>) future;
        }

        @SuppressWarnings("unchecked")
        @Override
        public FutureValue<Integer> size() {
            if (isAborted()) return (FutureValue<Integer>) FutureValue.uncompletedFuture();
            FutureValue<?> future = this.doRead(null, ReadOperation.ReadType.SIZE);
            return (FutureValue<Integer>) future;
        }

        @SuppressWarnings("unchecked")
        FutureValue<Object> doRead(K key, ReadOperation.ReadType type) {
            if (type != ReadOperation.ReadType.SIZE) {
                var ks = map.keyStatus(key);
                if (ks.isHeld() && !ks.isOwnedBy(this)) {
                    this.setAborted();
                    return (FutureValue<Object>) FutureValue.uncompletedFuture();
                }
            }

            ReadOperation<K, ?> ro = new ReadOperation<>(key, this, type);
            this.readSet.add((ReadOperation<K, Object>) ro);
            return (FutureValue<Object>) ro.future;
        }

        @Override
        public boolean isCommitted() {
            return state() == TransactionState.COMMITTED;
        }

        @Override
        public void commit() {
            this.validate();

            if (isAborted()){
                this.abort();
                return;
            }

            for (WriteOperation<K, V> wo : writeSet){
                wo.apply();
            }

            for (ReadOperation<K, Object> ro : readSet){
                ro.apply();
            }

            releaseLocksAndClearOps();
            this.map.epochTracker.leaveEpoch(tBegin);
            this.state = TransactionState.COMMITTED;
        }

        public void validate(){
            if (isAborted()) return;
            tCommit = map.epochTracker.newEpoch();
            for (ReadOperation<K, Object> readOperation : readSet){
                readOperation.validate();
            }
        }

        @Override
        public void abort() {
            releaseLocksAndClearOps();
        }

        void releaseLocksAndClearOps(){
            for (WriteOperation<K, V> wo : writeSet){
                KeyStatus s = map.keyStatus(wo.key);
                s.setNotHeld(txnId);
            }

            writeSet.clear();
            readSet.clear();
        }

        public TransactionID txnId(){
            return txnId;
        }

        public boolean isAborted(){
            return state == TransactionState.ABORTED;
        }

        public Transaction parent() {
            return null;
        }

        @Override
        public TransactionState state() {
            return state;
        }

        void setAborted(){
            this.state = TransactionState.ABORTED;
        }

        @SuppressWarnings("unchecked")
        static <V>FutureValue<V>  uncompletedFuture(){
           return  (FutureValue<V>) FutureValue.uncompletedFuture();
        }

        boolean tryHold(KeyStatus ks){
            //If we cannot hold the lock, return true the lock is already held
            return !ks.setHeld(txnId); //Don't spin, to prevent an issue where threads just tag team holding the lock, and we basically livelock
        }


        static class WriteOperation<K, V> implements Operation{
            private final K key;
            private final V value; //Null for remove types, could probably use K, V but not really worth it since the actual transaction provides compile time safety
            private final MvccTx<K, V> mvccTx;
            private final WriteType type;
            private final FutureValue<V> future;

            public WriteOperation(K key, V value, MvccTx<K, V> mvccTx, WriteType type) {
                this.key = key;
                this.value = value;
                this.mvccTx = mvccTx;
                this.type = type;
                this.future = new FutureValue<>();
            }
            public void apply() {
                var versionChain = mvccTx.map.versionChain(key);
                var prev =  versionChain.addNewVersion(value, mvccTx.tCommit, mvccTx.txnId);

                if (type == PUT && prev == null) mvccTx.size.incrementAndGet();
                else if (type == REMOVE && prev != null) mvccTx.size.decrementAndGet();

                future.complete(prev);

                //Removing previous versions
                if (versionChain.size() % mvccTx.map.versionThreshold == 0){
                    mvccTx.map.gcThread.submitCleanupRequest(key);
                }
            }


            enum WriteType{
                PUT, REMOVE
            }
        }


        private static class ReadOperation<K, V> implements Operation{
            private final K key;
            private final MvccTx<K, V> mvccTx;
            private final FutureValue<V> future;
            private final Version<V> seen;
            private final ReadType readType;

            public ReadOperation(K key, MvccTx<K, V> mvccTx, ReadType readType) {
                this.key = key;
                this.mvccTx = mvccTx;
                this.future = new FutureValue<>();
                this.readType = readType;
                if (readType != ReadType.SIZE){
                    this.seen = mvccTx.map.versionChain(key)
                            .findOverlap(mvccTx.tBegin);
                    return;
                }

                this.seen = null;
            }

            // We could add read semantic aware validation, but let us stick to the paper
            public void validate(){
                if (key == null || mvccTx.isAborted()) return; //If this is a size operation
                Version<V> overlapAtCommit = mvccTx.map.versionChain(key)
                        .findOverlap(mvccTx.tCommit); //Find if there's an overlap at commit time
                if (seen != overlapAtCommit){ //If the version we saw at txn begin isn't what we saw at commit time just abort the whole thing
                    mvccTx.setAborted();
                }
            }

            public void apply() {
                Object value;
                value = switch (readType) {
                    case GET ->  {
                       if (seen == null) yield null;
                       else yield seen.e();
                    }
                    case SIZE -> mvccTx.map.size.get() ; //Dirty reads are allowed for size, no way to really keep a version chain for size, even if we can, not worth the complexity
                    case CONTAINS -> seen != null && seen.e() != null;
                };

                future.complete(value);
            }

            enum ReadType{
                GET, CONTAINS, SIZE
            }
        }

        private interface Operation{
            void apply();
        }
    }

    //https://www.vldb.org/pvldb/vol10/p781-Wu.pdf The paper

}
