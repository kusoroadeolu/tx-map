package io.github.kusoroadeolu.txcoll;

import io.github.kusoroadeolu.ferrous.option.Option;
import org.jspecify.annotations.NonNull;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class FutureValue<V> {
    private final CompletableFuture<V> future;

    public FutureValue() {
        this.future = new CompletableFuture<>();
    }

    @SuppressWarnings("unchecked")
    public @NonNull Option<V> get(){
        try {
            if (future.isDone()){
                V value = future.get();
                if (value instanceof Option<?>) return (Option<V>) value;
                else return Option.ofNullable(value);
            }
            return Option.none();
        } catch (ExecutionException | InterruptedException _) {
            return Option.none();
        }
    }

    public boolean isComplete(){
        return future.isDone();
    }

    @SuppressWarnings("unchecked")
    public void complete(@NonNull Object value){
        future.complete((V)value);
    }
}
