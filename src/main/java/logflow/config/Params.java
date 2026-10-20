package logflow.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code params} of one source, stage or sink, with typed, checked getters.
 * Every error message says where it happened, e.g. {@code stage 2 (filter): missing parameter 'op'}.
 */
public final class Params {

    private final String where;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Set<String> used = new HashSet<>();

    @SuppressWarnings("unchecked")
    Params(String where, Object raw) {
        this.where = where;
        if (raw == null) {
            return;
        }
        if (!(raw instanceof Map)) {
            throw error("'params' must be a map like { key: value }, got: " + raw);
        }
        for (Map.Entry<Object, Object> e : ((Map<Object, Object>) raw).entrySet()) {
            values.put(String.valueOf(e.getKey()), e.getValue());
        }
    }

    /** A required text parameter. */
    public String string(String key) {
        Object v = value(key);
        if (!(v instanceof String)) {
            throw error("parameter '" + key + "' must be text, got: " + v);
        }
        return (String) v;
    }

    /** An optional text parameter. */
    public String string(String key, String defaultValue) {
        return values.containsKey(key) ? string(key) : defaultValue;
    }

    /** A required parameter of any type (text, number, …). */
    public Object value(String key) {
        used.add(key);
        if (!values.containsKey(key) || values.get(key) == null) {
            throw error("missing required parameter '" + key + "'");
        }
        return values.get(key);
    }

    /** A required list of texts, e.g. {@code [timestamp, clientIp]}. */
    public List<String> stringList(String key) {
        Object v = value(key);
        if (!(v instanceof List) || ((List<?>) v).isEmpty()) {
            throw error("parameter '" + key + "' must be a non-empty list like [a, b], got: " + v);
        }
        List<String> list = new ArrayList<>();
        for (Object item : (List<?>) v) {
            list.add(String.valueOf(item));
        }
        return Collections.unmodifiableList(list);
    }

    /** Fails on parameters nobody asked for — usually a typo. */
    void checkNoUnknown() {
        for (String key : values.keySet()) {
            if (!used.contains(key)) {
                throw error("unknown parameter '" + key + "' (allowed: " + used + ")");
            }
        }
    }

    public ConfigException error(String message) {
        return new ConfigException(where + ": " + message);
    }
}
