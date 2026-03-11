package io.github.kusoroadeolu.txmap;

import io.github.kusoroadeolu.ferrous.option.Option;
import org.jspecify.annotations.NonNull;


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

public class FutureValue<V> {
    private final CompletableFuture<V> future;
    private static final FutureValue<?> UNCOMPLETED_FUTURE = new FutureValue<>();

    public FutureValue() {
        this.future = new CompletableFuture<>();
    }

    public V get(){
        try {
            if (future.isDone())return future.get();
            return null;
        } catch (ExecutionException | InterruptedException _) {
            return null;
        }
    }

    public boolean isComplete(){
        return future.isDone();
    }

    @SuppressWarnings("unchecked")
    void complete(Object value){
        future.complete((V)value);
    }

    public static FutureValue<?> uncompletedFuture(){
        return UNCOMPLETED_FUTURE;
    }
}
