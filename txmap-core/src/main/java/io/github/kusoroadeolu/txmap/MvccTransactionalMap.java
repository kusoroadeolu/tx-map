package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.ferrous.option.Option;
import io.github.kusoroadeolu.txmap.VersionChain.Version;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


//Append only storage
//For garbage collection, the issue is knowing when a version is not visible to other transactions
// version.begin-ts <= tBegin < version.end-ts
public class MvccTransactionalMap<K, V> implements TransactionalMap<K, V>{
    private final CommitNumberGenerator commitNumberGenerator; //Incremented at commit time
    private final ConcurrentMap<K, VersionChain<V>> underlying;
    private final ConcurrentMap<K, KeyStatus> status; //Keeping the status to the key
    private final TransactionIDGenerator idGenerator;
    private final ActiveTransactions activeTransactions;
    private static final int VERSION_THRESHOLD = 100;

    public MvccTransactionalMap() {
        this.commitNumberGenerator = new CommitNumberGenerator();
        this.status = new ConcurrentHashMap<>();
        this.underlying = new ConcurrentHashMap<>();
        this.activeTransactions = new ActiveTransactions();
        this.idGenerator = new TransactionIDGenerator();
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
        var vMap = underlying;
        VersionChain<V> versionChain = vMap.get(key);
        if(versionChain == null) {
            versionChain = vMap.computeIfAbsent(key, _ -> new VersionChain<>());
        }

        return versionChain;
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
        private final Set<WriteOperation<K, V>> writeSet;
        private final Set<ReadOperation<K, Object>> readSet;
        private TransactionState state = TransactionState.IN_PROGRESS;


        public MvccTx(MvccTransactionalMap<K, V> map) {
            this.map = map;
            this.txnId = new TransactionID(map.idGenerator.newId());
            this.tBegin = map.commitNumberGenerator.currentCommitNo();
            map.activeTransactions.put(txnId, tBegin);
            this.readSet = new HashSet<>();
            this.writeSet = new HashSet<>();
        }

        @Override
        public FutureValue<Option<V>> put(K key, V value) {
            return this.doWrite(key, value);
        }

        @Override
        public FutureValue<Option<V>> remove(K key) {
            return this.doWrite(key, null);
        }



        FutureValue<Option<V>> doWrite(K key, V value){
            if (isAborted()) {
                return uncompletedFuture();
            }

            var ks = map.keyStatus(key);
            boolean alreadyHeld = this.tryHold(ks); //If we fail to hold the 'lock' and the lock holder hasnt committed just abort the whole tx
            if (alreadyHeld) { //Failed to hold write lock, abort
                this.setAborted();
                return uncompletedFuture();
            }

            WriteOperation<K, V> wo = new WriteOperation<>(key, value, this);
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
        public FutureValue<Option<V>> get(K key) {
            if (isAborted()) return uncompletedFuture();
            FutureValue<?> future = this.doRead(key, ReadOperation.ReadType.GET);
            return (FutureValue<Option<V>>) future;
        }

        @SuppressWarnings("unchecked")
        @Override
        public FutureValue<Option<Boolean>> containsKey(K key) {
            if (isAborted()) return  (FutureValue<Option<Boolean>>) FutureValue.uncompletedFuture();
            FutureValue<?> future = this.doRead(key, ReadOperation.ReadType.CONTAINS);
            return (FutureValue<Option<Boolean>>) future;
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
                var s = map.keyStatus(wo.key);
                s.setCommitted();
            }

            for (ReadOperation<K, Object> ro : readSet){
                ro.apply();
            }

            releaseLocksAndClearOps();
            this.map.activeTransactions.remove(txnId);
            this.state = TransactionState.COMMITTED;
        }

        public void validate(){
            if (isAborted()) return;
            tCommit = map.commitNumberGenerator.newCommitNo();
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

        @Override
        public Option<Transaction> parent() {
            return Option.none();
        }

        @Override
        public TransactionState state() {
            return state;
        }

        void setAborted(){
            this.state = TransactionState.ABORTED;
        }

        @SuppressWarnings("unchecked")
        static <V>FutureValue<Option<V>>  uncompletedFuture(){
           return  (FutureValue<Option<V>>) FutureValue.uncompletedFuture();
        }

        boolean tryHold(KeyStatus ks){
            while(!ks.setHeld(txnId)){
                if (!ks.isCommitted()){ //If the holding tx has not committed, fail, we should abort after this
                    return true; //Is held
                }
                Thread.onSpinWait();
            } //Spins until held, if the transaction is committed(i.e. that operation has modified the map), but we're waiting to acquire the lock

            return false;
        }


        private static class WriteOperation<K, V> implements Operation{
            private final K key;
            private final V value; //Null for remove types, could probably use K, V but not really worth it since the actual transaction provides compile time safety
            private final MvccTx<K, V> mvccTx;
            private final FutureValue<Option<V>> future;

            public WriteOperation(K key, V value, MvccTx<K, V> mvccTx) {
                this.key = key;
                this.value = value;
                this.mvccTx = mvccTx;
                this.future = new FutureValue<>();
            }

            public void apply() {
                var versionChain = mvccTx.map.versionChain(key);
                var prev =  versionChain.enqueueNewVersion(value, mvccTx.tCommit, mvccTx.txnId);
                future.complete(Option.ofNullable(prev));

                //Removing previous versions
                if (versionChain.size() >= VERSION_THRESHOLD){
                    ActiveTransactions activeTxns = mvccTx.map.activeTransactions.copy(); //We're getting a copy to prevent any race conditions while we're searching for the lowest tBegin
                    long minActiveTBegin = activeTxns.findMinActiveTBegin();
                    versionChain.removeUnreachableVersions(minActiveTBegin);
                }


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

            // We could add read semantic aware validation, but for now lets stick to the paper
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
                    case SIZE -> mvccTx.map.underlying.size(); //Dirty reads are allowed for size, no way to really keep a version chain for size, even if we can not worth the complexity
                    case CONTAINS -> seen != null && seen.e() != null;
                };

                future.complete(Option.ofNullable(value));
            }

            enum ReadType{
                GET, CONTAINS, SIZE
            }
        }

        private interface Operation{
            void apply();
        }
    }
}
