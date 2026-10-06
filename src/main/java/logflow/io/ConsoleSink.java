package logflow.io;

import java.io.PrintStream;
import java.util.Objects;
import java.util.function.Function;

import logflow.core.Sink;
import logflow.model.LogRecord;

/**
 * Prints every record it receives on its own line.
 * <p>
 * How a record becomes text is decided by a formatter function, so the sink itself does not
 * depend on any particular record type.
 */
public final class ConsoleSink<I> implements Sink<I> {

    private final PrintStream out;
    private final Function<? super I, String> formatter;

    /** Prints {@code String.valueOf(item)} to standard output. */
    public ConsoleSink() {
        this(System.out);
    }

    public ConsoleSink(PrintStream out) {
        this(out, String::valueOf);
    }

    public ConsoleSink(PrintStream out, Function<? super I, String> formatter) {
        this.out = Objects.requireNonNull(out, "out");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    /** A sink that prints each {@link LogRecord} as one readable line. */
    public static ConsoleSink<LogRecord> forLogRecords(PrintStream out) {
        return new ConsoleSink<>(out, LogRecordFormatter::format);
    }

    @Override
    public void consume(I item) {
        out.println(formatter.apply(item));
    }
}
