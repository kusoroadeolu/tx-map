package io.github.kusoroadeolu.txmap;

@FunctionalInterface
public interface Action<E, R> {
    R apply(E e);
}
