package logflow.io;

import java.io.PrintStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import logflow.core.Sink;
import logflow.model.LogRecord;

/**
 * Prints every {@link LogRecord} as one readable line, e.g.
 * <pre>2026-09-28 05:01:03Z  192.168.1.183    DELETE /index.html  200  36770 B  ua="Mozilla/5.0 ..."</pre>
 * Timestamps are shown in UTC.
 */
public final class ConsoleSink implements Sink<LogRecord> {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final PrintStream out;

    public ConsoleSink() {
        this(System.out);
    }

    public ConsoleSink(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    @Override
    public void consume(LogRecord r) {
        String query = r.attribute("query");
        String target = query == null ? r.path() : r.path() + "?" + query;
        out.println(String.format("%s  %-15s  %-6s %s  %d  %d B  ua=\"%s\"",
                TIME.format(r.timestamp()), r.clientIp(), r.method(), target,
                r.status(), r.bytes(), r.userAgent()));
    }
}
