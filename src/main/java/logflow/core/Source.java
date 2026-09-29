package logflow.core;

/** The start of a pipeline: produces records and pushes them into the emitter. */
public interface Source<O> {
    void produce(Emitter<O> out);
}
