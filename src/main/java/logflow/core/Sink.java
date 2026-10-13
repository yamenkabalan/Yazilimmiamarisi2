package logflow.core;

/** The end of a pipeline: consumes the records that reach it. */
public interface Sink<I> {

    void consume(I item);

    /** Lifecycle hook, called once before the first record (e.g. open a file). */
    default void open() {}

    /** Lifecycle hook, called once after the last record (e.g. flush and close a file). */
    default void close() {}
}
