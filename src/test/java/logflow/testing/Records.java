package logflow.testing;

import java.time.Instant;

import logflow.model.LogRecord;

/** Small factory for test records, so stage tests do not need the parser. */
public final class Records {

    private Records() {}

    public static LogRecord record(String time, String path, int status) {
        return LogRecord.builder()
                .timestamp(Instant.parse(time))
                .clientIp("10.0.0.1").method("GET").path(path).status(status).bytes(100)
                .userAgent("curl/8").raw("raw")
                .attribute("protocol", "HTTP/1.1")
                .build();
    }
}
