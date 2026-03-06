package io.github.kusoroadeolu.txmap;

import java.util.concurrent.atomic.AtomicLong;

public class CommitNumberGenerator {
    private final AtomicLong generator;

    public CommitNumberGenerator() {
        this.generator = new AtomicLong();
    }

    public long newCommitNo(){
        return generator.incrementAndGet();
    }

    public long currentCommitNo(){
        return generator.get();
    }


}
