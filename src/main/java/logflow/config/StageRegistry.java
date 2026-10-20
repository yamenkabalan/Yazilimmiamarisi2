package logflow.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import logflow.model.LogRecord;
import logflow.stage.FieldCondition;
import logflow.stage.ParserStage;
import logflow.stage.PredicateFilterStage;
import logflow.stage.ProjectionStage;

/**
 * Maps a stage type name from the config ("parse", "filter", …) to the factory that builds it.
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
