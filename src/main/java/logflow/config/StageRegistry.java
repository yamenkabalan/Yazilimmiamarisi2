package logflow.config;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import logflow.model.LogRecord;
import logflow.stage.FieldCondition;
import logflow.stage.ParserStage;
import logflow.stage.PredicateFilterStage;
import logflow.stage.ProjectionStage;
import logflow.stage.ValidationStage;

/**
 * Maps a stage type name from the config ("parse", "validate", "filter", …) to the factory that builds it.
 * <p>
 * Registration is data, not a {@code switch}: {@link #standard()} is the one table of built-in
 * stages, and a new stage is added with one more {@link #register} call — nothing else changes.
 * Each entry also records its input and output type, so the loader can reject a wrong order.
 */
public final class StageRegistry {

    /** One registered stage type. */
    public static final class Entry {
        final Class<?> input;
        final Class<?> output;
        final StageFactory factory;

        Entry(Class<?> input, Class<?> output, StageFactory factory) {
            this.input = input;
            this.output = output;
            this.factory = factory;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public StageRegistry register(String type, Class<?> input, Class<?> output, StageFactory factory) {
        entries.put(type, new Entry(input, output, factory));
        return this;
    }

    /** The entry for a type name, or {@code null} if unknown. */
    public Entry find(String type) {
        return entries.get(type);
    }

    public Set<String> types() {
        return entries.keySet();
    }

    /** The built-in stages — the registration table. */
    public static StageRegistry standard() {
        return new StageRegistry()
                .register("parse", String.class, LogRecord.class, p -> {
                    String format = p.string("format", "clf");
                    if (!format.equals("clf")) {
                        throw p.error("unsupported format '" + format + "' (supported: clf)");
                    }
                    return new ParserStage();
                })
                .register("validate", LogRecord.class, LogRecord.class, p -> {
                    ValidationStage.Builder b = ValidationStage.builder();

                    if (p.has("required")) {
                        for (String f : p.stringList("required")) {
                            if (!LogRecord.FIELDS.contains(f)) {
                                throw p.error("unknown field in required: '" + f + "'. Allowed: " + LogRecord.FIELDS);
                            }
                        }
                        b.required(p.stringList("required"));
                    }

                    if (p.has("ranges")) {
                        Map<String, Object> ranges = p.map("ranges");
                        for (Map.Entry<String, Object> e : ranges.entrySet()) {
                            String f = e.getKey();
                            if (!LogRecord.FIELDS.contains(f)) {
                                throw p.error("unknown field in ranges: '" + f + "'");
                            }
                            if (e.getValue() instanceof Map) {
                                Map<?, ?> rMap = (Map<?, ?>) e.getValue();
                                Long min = rMap.get("min") instanceof Number ? ((Number) rMap.get("min")).longValue() : null;
                                Long max = rMap.get("max") instanceof Number ? ((Number) rMap.get("max")).longValue() : null;
                                b.range(f, min, max);
                            }
                        }
                    }

                    if (p.has("minStatus")) b.minStatus(p.number("minStatus").intValue());
                    if (p.has("maxStatus")) b.maxStatus(p.number("maxStatus").intValue());
                    if (p.has("minBytes")) b.minBytes(p.number("minBytes").longValue());
                    if (p.has("maxBytes")) b.maxBytes(p.number("maxBytes").longValue());

                    if (p.has("timestamp")) {
                        Map<String, Object> ts = p.map("timestamp");
                        if (ts.get("min") != null) b.minTimestamp(Instant.parse(String.valueOf(ts.get("min"))));
                        if (ts.get("max") != null) b.maxTimestamp(Instant.parse(String.valueOf(ts.get("max"))));
                    }
                    if (p.has("minTimestamp")) b.minTimestamp(Instant.parse(p.string("minTimestamp")));
                    if (p.has("maxTimestamp")) b.maxTimestamp(Instant.parse(p.string("maxTimestamp")));

                    if (p.has("ip")) b.checkIp(p.bool("ip", false));
                    if (p.has("maxRawLength")) b.maxRawLength(p.number("maxRawLength").intValue());

                    return b.build();
                })
                .register("filter", LogRecord.class, LogRecord.class, p ->
                        new PredicateFilterStage(FieldCondition.of(p.string("field"), p.string("op"), p.value("value"))))
                .register("project", LogRecord.class, Map.class, p -> {
                    for (String field : p.stringList("fields")) {
                        if (!LogRecord.FIELDS.contains(field)) {
                            throw p.error("unknown field '" + field + "'. Known fields: " + LogRecord.FIELDS);
                        }
                    }
                    return new ProjectionStage(p.stringList("fields"));
                });
    }
}
