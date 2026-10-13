package logflow.stage;

import static logflow.testing.Records.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import logflow.model.LogRecord;
import logflow.testing.CollectingEmitter;

class PredicateFilterStageTest {

    private static final List<LogRecord> INPUT = Arrays.asList(
            record("2026-09-28T05:00:00Z", "/api/users", 200),
            record("2026-09-28T05:10:00Z", "/api/orders", 404),
            record("2026-09-28T05:20:00Z", "/login", 401),
            record("2026-09-28T05:30:00Z", "/index.html", 500));

    private static List<String> paths(PredicateFilterStage stage) {
        CollectingEmitter<LogRecord> out = new CollectingEmitter<>();
        for (LogRecord r : INPUT) {
            stage.process(r, out);
        }
        return out.items().stream().map(LogRecord::path).collect(Collectors.toList());
    }

    @Test
    void minStatusKeepsErrorsOnly() {
        assertEquals(Arrays.asList("/api/orders", "/login", "/index.html"), paths(PredicateFilterStage.minStatus(400)));
    }

    @Test
    void pathPrefixKeepsMatchingPaths() {
        assertEquals(Arrays.asList("/api/users", "/api/orders"), paths(PredicateFilterStage.pathPrefix("/api")));
    }

    @Test
    void betweenIsInclusiveFromExclusiveTo() {
        PredicateFilterStage stage = PredicateFilterStage.between(
                Instant.parse("2026-09-28T05:10:00Z"), Instant.parse("2026-09-28T05:30:00Z"));
        assertEquals(Arrays.asList("/api/orders", "/login"), paths(stage));
    }

    @Test
    void anyLambdaCanBeTheCondition() {
        assertEquals(Arrays.asList("/login"), paths(new PredicateFilterStage(r -> r.path().equals("/login"))));
    }

    @Test
    void nullConditionIsRejected() {
        assertThrows(NullPointerException.class, () -> new PredicateFilterStage(null));
    }
}
