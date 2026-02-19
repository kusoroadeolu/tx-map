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

    public @NonNull Option<V> get(){
        try {
            return future.isDone() ? Option.ofNullable(future.get()) : Option.none();
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
