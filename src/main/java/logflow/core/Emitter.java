package logflow.core;

/** The connector between two components: whoever holds it can push items downstream. */
public interface Emitter<T> {
    void emit(T item);
}
