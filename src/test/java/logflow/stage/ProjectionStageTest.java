package logflow.stage;

import static logflow.testing.Records.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import logflow.testing.CollectingEmitter;

class ProjectionStageTest {

    private static Map<String, String> project(String... fields) {
        CollectingEmitter<Map<String, String>> out = new CollectingEmitter<>();
        new ProjectionStage(fields).process(record("2026-09-28T05:00:00Z", "/api/users", 404), out);
        return out.single();
    }

    @Test
    void keepsOnlyTheChosenFieldsInTheGivenOrder() {
        Map<String, String> m = project("status", "timestamp", "clientIp");

        assertEquals(Arrays.asList("status", "timestamp", "clientIp"), Arrays.asList(m.keySet().toArray()));
        assertEquals("404", m.get("status"));
        assertEquals("2026-09-28T05:00:00Z", m.get("timestamp"));
        assertEquals("10.0.0.1", m.get("clientIp"));
    }

    @Test
    void allCoreFieldsCanBeSelected() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("method", "GET");
        expected.put("path", "/api/users");
        expected.put("bytes", "100");
        expected.put("userAgent", "curl/8");
        assertEquals(expected, project("method", "path", "bytes", "userAgent"));
    }

    @Test
    void otherNamesAreReadFromAttributes() {
        Map<String, String> m = project("protocol", "referer");
        assertEquals("HTTP/1.1", m.get("protocol"));
        assertEquals("-", m.get("referer"), "missing attribute becomes '-'");
    }

    @Test
    void outputCannotBeModified() {
        Map<String, String> m = project("status");
        assertThrows(UnsupportedOperationException.class, () -> m.put("x", "y"));
    }

    @Test
    void atLeastOneFieldIsRequired() {
        assertThrows(IllegalArgumentException.class, ProjectionStage::new);
    }
}
