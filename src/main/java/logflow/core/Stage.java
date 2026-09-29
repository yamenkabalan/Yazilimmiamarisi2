package logflow.core;

/**
 * A processing step in the pipeline. A stage receives one input and may emit
 * zero, one or many outputs through the {@link Emitter}.
 *
 * @param <I> input type
 * @param <O> output type
 */
public interface Stage<I, O> {

    void process(I input, Emitter<O> out) throws StageException;

    /** Lifecycle hook, called once before the first record. Unused in v1. */
    default void open() {}

    /** Lifecycle hook, called once after the last record. Unused in v1. */
    default void close() {}
}
