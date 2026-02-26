package io.github.kusoroadeolu.txmap.uncommitted;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LockHolder<K, V> {
    private final ConcurrentMap<K, Lock> map;

    public LockHolder() {
        this.map = new ConcurrentHashMap<>();
    }

    Lock getLock(K key){
        var lock = map.get(key);
        if (lock == null) lock = map.computeIfAbsent(key, _ -> new ReentrantLock());
        return lock;
    }

}
