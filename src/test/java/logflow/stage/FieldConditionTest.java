package logflow.stage;

import static logflow.testing.Records.record;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import logflow.model.LogRecord;

class FieldConditionTest {

    private final LogRecord r = record("2026-09-28T05:00:00Z", "/api/users", 404);

    @Test
    void numericOps() {
        assertTrue(FieldCondition.of("status", ">=", 400).test(r));
        assertTrue(FieldCondition.of("status", ">", 403).test(r));
        assertFalse(FieldCondition.of("status", "<", 400).test(r));
        assertTrue(FieldCondition.of("status", "<=", 404).test(r));
    }

    @Test
    void textOps() {
        assertTrue(FieldCondition.of("method", "==", "get").test(r));
        assertTrue(FieldCondition.of("status", "==", 404).test(r));
        assertTrue(FieldCondition.of("method", "!=", "POST").test(r));
        assertTrue(FieldCondition.of("path", "startsWith", "/api").test(r));
        assertTrue(FieldCondition.of("path", "contains", "users").test(r));
    }

    @Test
    void badCombinationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldCondition.of("nope", "==", 1));
        assertThrows(IllegalArgumentException.class, () -> FieldCondition.of("status", "=~", 1));
        assertThrows(IllegalArgumentException.class, () -> FieldCondition.of("path", ">", 1));
        assertThrows(IllegalArgumentException.class, () -> FieldCondition.of("status", ">", "x"));
    }
}
