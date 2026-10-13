package logflow.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Paths;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class OptionsTest {

    @Test
    void parsesAllOptions() {
        Options o = Options.parse(new String[] {
                "data/in.log", "--min-status", "400", "--path-prefix", "/api",
                "--fields", "timestamp,clientIp,status", "--out", "r.txt",
                "--from", "2026-09-28T05:00:00Z", "--to", "2026-09-28T06:00:00Z"});

        assertEquals(Paths.get("data/in.log"), o.input);
        assertEquals(Integer.valueOf(400), o.minStatus);
        assertEquals("/api", o.pathPrefix);
        assertArrayEquals(new String[] {"timestamp", "clientIp", "status"}, o.fields);
        assertEquals(Paths.get("r.txt"), o.out);
        assertEquals(Instant.parse("2026-09-28T05:00:00Z"), o.from);
        assertEquals(Instant.parse("2026-09-28T06:00:00Z"), o.to);
    }

    @Test
    void defaultsWhenOnlyInputIsGiven() {
        Options o = Options.parse(new String[] {"in.log"});
        assertNull(o.minStatus);
        assertNull(o.pathPrefix);
        assertEquals(Paths.get("out", "result.log"), o.out);
        assertArrayEquals(Options.DEFAULT_FIELDS.split(","), o.fields);
    }

    @Test
    void badArgumentsAreReported() {
        assertThrows(IllegalArgumentException.class, () -> Options.parse(new String[] {}));
        assertThrows(IllegalArgumentException.class, () -> Options.parse(new String[] {"a.log", "b.log"}));
        assertThrows(IllegalArgumentException.class, () -> Options.parse(new String[] {"a.log", "--min-status"}));
        assertThrows(IllegalArgumentException.class, () -> Options.parse(new String[] {"a.log", "--min-status", "x"}));
        assertThrows(IllegalArgumentException.class, () -> Options.parse(new String[] {"a.log", "--from", "yesterday"}));
        assertThrows(IllegalArgumentException.class, () -> Options.parse(new String[] {"a.log", "--color", "red"}));
    }
}
