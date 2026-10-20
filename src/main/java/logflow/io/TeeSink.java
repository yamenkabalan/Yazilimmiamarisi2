package logflow.io;

import java.util.Objects;

import logflow.core.Sink;

/** Sends every item to two sinks, e.g. a file and the terminal. */
public final class TeeSink<T> implements Sink<T> {

    private final Sink<T> first;
    private final Sink<T> second;

    public TeeSink(Sink<T> first, Sink<T> second) {
        this.first = Objects.requireNonNull(first, "first");
        this.second = Objects.requireNonNull(second, "second");
    }

    @Override
    public void open() {
        first.open();
        second.open();
    }

    @Override
    public void consume(T item) {
        first.consume(item);
        second.consume(item);
    }

    @Override
    public void close() {
        try {
            first.close();
        } finally {
            second.close();
        }
    }
}
