package logflow.core;

/** The end of a pipeline: consumes the records that reach it. */
public interface Sink<I> {
    void consume(I item);
}
