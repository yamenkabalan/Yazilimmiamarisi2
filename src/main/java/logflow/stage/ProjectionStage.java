package logflow.stage;

import java.util.ArrayList;
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
 */
public final class ProjectionStage implements Stage<LogRecord, Map<String, String>> {

    private final List<String> fields;

    public ProjectionStage(String... fields) {
        this(Arrays.asList(fields));
    }

    public ProjectionStage(List<String> fields) {
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("at least one field is required");
        }
        this.fields = new ArrayList<>(fields);
    }

    @Override
    public void process(LogRecord r, Emitter<Map<String, String>> out) {
        Map<String, String> projected = new LinkedHashMap<>();
        for (String field : fields) {
            projected.put(field, r.field(field));
        }
        out.emit(Collections.unmodifiableMap(projected));
    }
}
