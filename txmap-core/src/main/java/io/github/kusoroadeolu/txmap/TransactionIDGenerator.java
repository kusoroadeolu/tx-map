package io.github.kusoroadeolu.txmap;

import java.util.concurrent.atomic.AtomicLong;

public class TransactionIDGenerator {
    private final AtomicLong generator;

    public TransactionIDGenerator() {
        this.generator = new AtomicLong();
    }

    public long newId(){
        return generator.incrementAndGet();
    }
}
