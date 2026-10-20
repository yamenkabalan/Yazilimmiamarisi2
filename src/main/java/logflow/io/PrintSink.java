package logflow.io;

import java.io.PrintStream;
import java.util.Objects;

import logflow.core.Sink;

/** Prints each item as one line to a stream (the terminal by default). */
public final class PrintSink<T> implements Sink<T> {

    private final PrintStream out;
    private long written;

    public PrintSink() {
        this(System.out);
    }

    public PrintSink(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    @Override
    public void open() {
        written = 0;
    }

    @Override
    public void consume(T item) {
        out.println(item);
        written++;
    }

    @Override
    public void close() {
        out.flush();
    }

    /** Number of lines printed since the last {@link #open()}. */
    public long writtenCount() {
        return written;
    }
}
