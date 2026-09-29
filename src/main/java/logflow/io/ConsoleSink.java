package logflow.io;

import java.io.PrintStream;
import java.util.Objects;

import logflow.core.Sink;

/** Prints every record it receives on its own line. */
public final class ConsoleSink<I> implements Sink<I> {

    private final PrintStream out;

    public ConsoleSink() {
        this(System.out);
    }

    public ConsoleSink(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    @Override
    public void consume(I item) {
        out.println(item);
    }
}
