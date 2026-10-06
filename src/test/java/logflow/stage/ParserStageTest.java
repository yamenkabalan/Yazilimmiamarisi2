package logflow.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import logflow.model.LogRecord;
import logflow.testing.CollectingEmitter;

/**
 * ParserStage tests. None of them touch the file system: each test creates the stage,
 * hands it a String, and captures the output with a {@link CollectingEmitter}.
 */
class ParserStageTest {

    private static final String VALID =
            "192.168.1.183 - - [28/Sep/2026:08:01:03 +0300] \"DELETE /index.html HTTP/1.1\" 200 36770 "
                    + "\"https://www.google.com/\" \"Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/128.0\"";

    private ParserStage parser;
    private CollectingEmitter<LogRecord> out;

    @BeforeEach
    void setUp() {
        parser = new ParserStage();
        parser.open();
        out = new CollectingEmitter<>();
    }

    private void assertSkipped(String line) {
        parser.process(line, out);
        assertTrue(out.isEmpty(), "nothing should be emitted for: " + line);
        assertEquals(1, parser.malformedCount());
    }

    // ---- the 8 required cases ----------------------------------------------------------

    @Test
    @DisplayName("1. valid line is parsed into all fields")
    void validLine() {
        parser.process(VALID, out);

        LogRecord r = out.single();
        assertEquals(Instant.parse("2026-09-28T05:01:03Z"), r.timestamp());
        assertEquals("192.168.1.183", r.clientIp());
        assertEquals("DELETE", r.method());
        assertEquals("/index.html", r.path());
        assertEquals(200, r.status());
        assertEquals(36770L, r.bytes());
        assertEquals("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/128.0", r.userAgent());
        assertEquals("HTTP/1.1", r.attribute("protocol"));
        assertEquals("https://www.google.com/", r.attribute("referer"));
        assertEquals(VALID, r.raw());
        assertEquals(0, parser.malformedCount());
    }

    @Test
    @DisplayName("2. line with a missing field is skipped and counted")
    void missingField() {
        // no byte-count field
        assertSkipped("10.0.0.1 - - [28/Sep/2026:08:01:03 +0300] \"GET / HTTP/1.1\" 200");
    }

    @Test
    @DisplayName("3. malformed timestamp is skipped and counted")
    void badTimestamp() {
        assertSkipped("10.0.0.1 - - [99/Foo/2026:25:61:03 +0300] \"GET / HTTP/1.1\" 200 512");
    }

    @Test
    @DisplayName("4. malformed status code is skipped and counted")
    void badStatus() {
        assertSkipped("10.0.0.1 - - [28/Sep/2026:08:01:03 +0300] \"GET / HTTP/1.1\" OK 512");
    }

    @Test
    @DisplayName("5. empty line is skipped and counted")
    void emptyLine() {
        assertSkipped("");
    }

    @Test
    @DisplayName("6. extra whitespace around and between fields is tolerated")
    void extraWhitespace() {
        parser.process("   10.0.0.1  -  -   [28/Sep/2026:08:01:03 +0300]  \"GET  /a  HTTP/1.1\"   404   12   ", out);

        LogRecord r = out.single();
        assertEquals("10.0.0.1", r.clientIp());
        assertEquals("GET", r.method());
        assertEquals("/a", r.path());
        assertEquals(404, r.status());
        assertEquals(12L, r.bytes());
    }

    @Test
    @DisplayName("7. quoted user agent containing spaces is kept whole")
    void quotedUserAgentWithSpaces() {
        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 Safari/605.1.15";
        parser.process("10.0.4.235 - - [28/Sep/2026:08:01:44 +0300] \"PUT /static/js/app.js HTTP/1.1\" 200 30862 "
                + "\"-\" \"" + ua + "\"", out);

        LogRecord r = out.single();
        assertEquals(ua, r.userAgent());
        assertNull(r.attribute("referer"), "a '-' referer means no referer");
    }

    @Test
    @DisplayName("8. query string is split from the path into the 'query' attribute")
    void queryString() {
        parser.process("10.0.0.1 - - [28/Sep/2026:08:01:03 +0300] \"GET /search?q=log+flow&page=2 HTTP/1.1\" 200 99", out);

        LogRecord r = out.single();
        assertEquals("/search", r.path());
        assertEquals("q=log+flow&page=2", r.attribute("query"));
    }

    // ---- additional cases --------------------------------------------------------------

    @Test
    @DisplayName("plain CLF without referer/user agent is accepted; '-' bytes means 0")
    void plainClfWithDashBytes() {
        parser.process("127.0.0.1 - frank [10/Oct/2000:13:55:36 -0700] \"GET /apache_pb.gif HTTP/1.0\" 304 -", out);

        LogRecord r = out.single();
        assertEquals(Instant.parse("2000-10-10T20:55:36Z"), r.timestamp());
        assertEquals("frank", r.attribute("user"));
        assertEquals(0L, r.bytes());
        assertEquals("-", r.userAgent());
        assertEquals(304, r.status());
    }

    @Test
    @DisplayName("status code outside 100..599 is skipped")
    void statusOutOfRange() {
        assertSkipped("10.0.0.1 - - [28/Sep/2026:08:01:03 +0300] \"GET / HTTP/1.1\" 999 1");
    }

    @Test
    @DisplayName("request without method/path/protocol is skipped")
    void badRequestLine() {
        assertSkipped("10.0.0.1 - - [28/Sep/2026:08:01:03 +0300] \"GARBAGE\" 400 0");
    }

    @Test
    @DisplayName("non-numeric byte count is skipped")
    void badBytes() {
        assertSkipped("10.0.0.1 - - [28/Sep/2026:08:01:03 +0300] \"GET / HTTP/1.1\" 200 lots");
    }

    @Test
    @DisplayName("null input is skipped")
    void nullLine() {
        assertSkipped(null);
    }

    @Test
    @DisplayName("malformed counter accumulates over many lines and resets on open()")
    void countersAccumulateAndReset() {
        parser.process(VALID, out);
        parser.process("junk", out);
        parser.process(VALID, out);
        parser.process("", out);

        assertEquals(2, out.size());
        assertEquals(2, parser.malformedCount());

        parser.open();
        assertEquals(0, parser.malformedCount());
    }
}
