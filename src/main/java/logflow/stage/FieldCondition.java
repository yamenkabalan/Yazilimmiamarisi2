package logflow.stage;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import logflow.model.LogRecord;

/**
 * Builds a filter condition from three plain values, as written in a config file:
 * {@code field: status, op: ">=", value: 400}.
 */
public final class FieldCondition {

    public static final List<String> OPS = Arrays.asList("==", "!=", ">", ">=", "<", "<=", "startsWith", "contains");
    private static final List<String> NUMERIC_FIELDS = Arrays.asList("status", "bytes");

    private FieldCondition() {}

    /** @throws IllegalArgumentException with a readable message if the combination makes no sense */
    public static Predicate<LogRecord> of(String field, String op, Object value) {
        if (!LogRecord.FIELDS.contains(field)) {
            throw new IllegalArgumentException("unknown field '" + field + "'. Known fields: " + LogRecord.FIELDS);
        }
        if (!OPS.contains(op)) {
            throw new IllegalArgumentException("unknown op '" + op + "'. Known ops: " + OPS);
        }
        String text = String.valueOf(value);
        switch (op) {
            case "==":         return r -> r.field(field).equalsIgnoreCase(text);
            case "!=":         return r -> !r.field(field).equalsIgnoreCase(text);
            case "startsWith": return r -> r.field(field).startsWith(text);
            case "contains":   return r -> r.field(field).contains(text);
            default:           break;   // >, >=, <, <= need numbers
        }
        if (!NUMERIC_FIELDS.contains(field)) {
            throw new IllegalArgumentException("op '" + op + "' needs a numeric field " + NUMERIC_FIELDS + ", got '" + field + "'");
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("op '" + op + "' needs a number as value, got '" + text + "'");
        }
        long n = ((Number) value).longValue();
        switch (op) {
            case ">":  return r -> Long.parseLong(r.field(field)) > n;
            case ">=": return r -> Long.parseLong(r.field(field)) >= n;
            case "<":  return r -> Long.parseLong(r.field(field)) < n;
            default:   return r -> Long.parseLong(r.field(field)) <= n;
        }
    }
}
