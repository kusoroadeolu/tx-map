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
            return Option.ofNullable(future.get());
        } catch (ExecutionException | InterruptedException _) {
            return Option.none();
        }
    }

    @SuppressWarnings("unchecked")
    public void complete(@NonNull Object value){
        future.complete((V)value);
    }
}
