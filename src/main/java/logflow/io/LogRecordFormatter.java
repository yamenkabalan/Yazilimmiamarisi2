package logflow.io;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import logflow.model.LogRecord;

/**
 * Turns a {@link LogRecord} into one readable line, e.g.
 * <pre>2026-09-28 05:01:03Z  192.168.1.183    DELETE /index.html  200  36770 B  ua="Mozilla/5.0 ..."</pre>
 * Timestamps are shown in UTC.
 */
public final class LogRecordFormatter {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private LogRecordFormatter() {}

    public static String format(LogRecord r) {
        String query = r.attribute("query");
        String target = query == null ? r.path() : r.path() + "?" + query;
        return String.format("%s  %-15s  %-6s %s  %d  %d B  ua=\"%s\"",
                TIME.format(r.timestamp()), r.clientIp(), r.method(), target,
                r.status(), r.bytes(), r.userAgent());
    }
}
