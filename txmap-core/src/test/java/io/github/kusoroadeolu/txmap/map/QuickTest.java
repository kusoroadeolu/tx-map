package io.github.kusoroadeolu.txmap.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class QuickTest {
    @Test
    public void testDefaultLockWrapper(){

        Lock lock = new ReentrantLock();
        DefaultTransactionalMap.LockWrapper wrapper = new DefaultTransactionalMap.LockWrapper(DefaultTransactionalMap.LockType.READ, Operation.SizeOperation.SIZE, lock);
        DefaultTransactionalMap.LockWrapper wrapper2 = new DefaultTransactionalMap.LockWrapper(DefaultTransactionalMap.LockType.READ, Operation.SizeOperation.SIZE, null);
        Assertions.assertEquals(wrapper2, wrapper);

    }
}
