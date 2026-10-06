package logflow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class LogRecordTest {

    private static LogRecord.Builder sample() {
        return LogRecord.builder()
                .timestamp(Instant.parse("2026-09-28T05:01:03Z"))
                .clientIp("10.0.0.1").method("GET").path("/").status(200).bytes(5)
                .userAgent("curl/8").raw("raw line")
                .attribute("protocol", "HTTP/1.1");
    }

    @Test
    void attributesCannotBeModified() {
        LogRecord r = sample().build();
        assertThrows(UnsupportedOperationException.class, () -> r.attributes().put("x", "y"));
    }

    @Test
    void builderChangesDoNotLeakIntoBuiltRecord() {
        LogRecord.Builder b = sample();
        LogRecord r = b.build();
        b.attribute("later", "value");
        assertEquals(1, r.attributes().size());
    }

    @Test
    void requiredFieldsAreChecked() {
        assertThrows(NullPointerException.class, () -> sample().timestamp(null).build());
        assertThrows(NullPointerException.class, () -> sample().raw(null).build());
    }
}
