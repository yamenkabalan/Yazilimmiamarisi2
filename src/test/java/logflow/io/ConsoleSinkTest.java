package logflow.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import logflow.model.LogRecord;

/** The sink writes to an in-memory PrintStream, so no console or file is involved. */
class ConsoleSinkTest {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintStream stream = new PrintStream(buffer, true);

    private String printed() throws UnsupportedEncodingException {
        return buffer.toString("UTF-8").replace(System.lineSeparator(), "\n");
    }

    @Test
    void printsLogRecordAsOneReadableLine() throws Exception {
        LogRecord r = LogRecord.builder()
                .timestamp(Instant.parse("2026-09-28T05:01:03Z"))
                .clientIp("10.0.0.1").method("GET").path("/search").status(200).bytes(512)
                .userAgent("curl/8.0").raw("raw")
                .attribute("query", "q=x")
                .build();

        ConsoleSink.forLogRecords(stream).consume(r);

        assertEquals("2026-09-28 05:01:03Z  10.0.0.1         GET    /search?q=x  200  512 B  ua=\"curl/8.0\"\n",
                printed());
    }

    @Test
    void defaultFormatterUsesToString() throws Exception {
        new ConsoleSink<Integer>(stream).consume(42);
        assertEquals("42\n", printed());
    }
}
