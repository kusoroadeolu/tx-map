package io.github.kusoroadeolu.txmap;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

//A simple map holding the tBegin numbers of each current transaction, when a key's version chain reaches a certain size,
// the write operation working on that key clears all unreachable versions i.e. V.endts <= tBegin
public class ActiveTransactionsKeeper {
    private final ConcurrentMap<TransactionID, Long> map;
    private final Queue<ModifyRequest> requests;
    private final Thread.Builder.OfVirtual thread;
    private volatile long minActiveTBegin = Long.MAX_VALUE;

    public ActiveTransactionsKeeper() {
        this.map = new ConcurrentHashMap<>();
        this.requests = new ConcurrentLinkedQueue<>();
        this.thread = Thread.ofVirtual();
        this.start();
    }


    void put(TransactionID txnId, long tBegin){
        this.requests.add(new ModifyRequest(txnId, tBegin, ModifyType.PUT));
    }

    void remove(TransactionID txnId){
        this.requests.add(new ModifyRequest(txnId, 0, ModifyType.REMOVE));

    }

    void start(){
        thread.start(() -> {
            while (true){ //TODO add bool flag for shutdown
                ModifyRequest request = null;
                while ((request = requests.poll()) != null){
                    switch (request.type){
                        case PUT -> {
                            map.put(request.txnId, request.tBegin);
                            if (request.tBegin < minActiveTBegin) minActiveTBegin = request.tBegin;
                        }

                        case REMOVE -> {
                            long tBegin = map.remove(request.txnId);
                            if (tBegin <= minActiveTBegin){
                                minActiveTBegin = searchNextMinActiveTBegin();
                            }
                        }
                    }
                }
            }
        });
    }

    long searchNextMinActiveTBegin(){
        Collection<Long> tBeginColl = map.values();
        long min = 0;
        int count = 0;
        for (long l : tBeginColl){
            if (count++ == 0 || l < min){
                min = l;
            }
        }

        return min;
    }

    //Should only be called if a transaction has copied this map onto its thread stack
    long findMinActiveTBegin(){
        return minActiveTBegin;
    }

    private record ModifyRequest(TransactionID txnId, long tBegin, ModifyType type){

    }

    private enum ModifyType{
        PUT, REMOVE
    }
}
