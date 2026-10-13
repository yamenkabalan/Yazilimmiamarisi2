package logflow.stage;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import logflow.core.Emitter;
import logflow.core.Stage;
import logflow.model.LogRecord;

/**
 * Reduces each record to the chosen fields only, e.g. {@code timestamp, clientIp, status}.
 * Data minimisation: smaller output, and fields that are not needed (user agent, IP…) never leave.
 * A name that is not a core field is looked up in {@link LogRecord#attributes()} ("-" if absent).
 */
public final class ProjectionStage implements Stage<LogRecord, Map<String, String>> {

    private final List<String> fields;

    public ProjectionStage(String... fields) {
        if (fields.length == 0) {
            throw new IllegalArgumentException("at least one field is required");
        }
        this.fields = Arrays.asList(fields.clone());
    }

    @Override
    public void process(LogRecord r, Emitter<Map<String, String>> out) {
        Map<String, String> projected = new LinkedHashMap<>();
        for (String field : fields) {
            projected.put(field, valueOf(r, field));
        }
        out.emit(Collections.unmodifiableMap(projected));
    }

    private static String valueOf(LogRecord r, String field) {
        switch (field) {
            case "timestamp": return r.timestamp().toString();
            case "clientIp":  return r.clientIp();
            case "method":    return r.method();
            case "path":      return r.path();
            case "status":    return String.valueOf(r.status());
            case "bytes":     return String.valueOf(r.bytes());
            case "userAgent": return r.userAgent();
            default:          return r.attributes().getOrDefault(field, "-");
        }
    }
}
