package io.github.kusoroadeolu.txmap;

public interface Combiner<E> {
    <R>R combine(Action<E, R> action);

    E e();
}
