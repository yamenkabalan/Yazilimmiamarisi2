package logflow.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import logflow.core.Record;

/**
 * One parsed access-log entry — the domain model that flows through the pipeline from v2 on.
 * <p>
 * Immutable: all fields are final, and {@link #attributes()} is an unmodifiable copy.
 * Instances are created with {@link #builder()} (Java 8 has no {@code record} type).
 * <p>
 * <b>Why {@code attributes}?</b> The record format must stay open for extension. Later stages
 * (weeks 5–7) will attach data that today's parser knows nothing about (e.g. geo-location,
 * classification, error info). They add entries to this map instead of adding new fields,
 * so neither this class nor the parser has to change.
 */
public final class LogRecord implements Record {

    private final Instant timestamp;
    private final String clientIp;
    private final String method;
    private final String path;
    private final int status;
    private final long bytes;
    private final String userAgent;
    private final Map<String, String> attributes;
    private final String raw;

    private LogRecord(Builder b) {
        this.timestamp = Objects.requireNonNull(b.timestamp, "timestamp");
        this.clientIp = Objects.requireNonNull(b.clientIp, "clientIp");
        this.method = Objects.requireNonNull(b.method, "method");
        this.path = Objects.requireNonNull(b.path, "path");
        this.status = b.status;
        this.bytes = b.bytes;
        this.userAgent = Objects.requireNonNull(b.userAgent, "userAgent");
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(b.attributes));
        this.raw = Objects.requireNonNull(b.raw, "raw");
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A builder pre-filled with this record's values — the way to "modify" an immutable record. */
    public Builder toBuilder() {
        Builder b = new Builder()
                .timestamp(timestamp).clientIp(clientIp).method(method).path(path)
                .status(status).bytes(bytes).userAgent(userAgent).raw(raw);
        b.attributes.putAll(attributes);
        return b;
    }

    public Instant timestamp() { return timestamp; }
    public String clientIp() { return clientIp; }
    public String method() { return method; }
    public String path() { return path; }
    public int status() { return status; }
    public long bytes() { return bytes; }
    public String userAgent() { return userAgent; }
    public Map<String, String> attributes() { return attributes; }

    /** Returns one attribute, or {@code null} if absent. */
    public String attribute(String key) {
        return attributes.get(key);
    }

    @Override
    public String raw() { return raw; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LogRecord)) return false;
        LogRecord r = (LogRecord) o;
        return status == r.status && bytes == r.bytes
                && timestamp.equals(r.timestamp) && clientIp.equals(r.clientIp)
                && method.equals(r.method) && path.equals(r.path)
                && userAgent.equals(r.userAgent) && attributes.equals(r.attributes)
                && raw.equals(r.raw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, clientIp, method, path, status, bytes, userAgent, attributes, raw);
    }

    @Override
    public String toString() {
        return "LogRecord[timestamp=" + timestamp + ", clientIp=" + clientIp + ", method=" + method
                + ", path=" + path + ", status=" + status + ", bytes=" + bytes
                + ", userAgent=" + userAgent + ", attributes=" + attributes + "]";
    }

    /** Collects values, then {@link #build()} creates the immutable record. */
    public static final class Builder {
        private Instant timestamp;
        private String clientIp;
        private String method;
        private String path;
        private int status;
        private long bytes;
        private String userAgent = "-";
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private String raw;

        private Builder() {}

        public Builder timestamp(Instant v) { this.timestamp = v; return this; }
        public Builder clientIp(String v) { this.clientIp = v; return this; }
        public Builder method(String v) { this.method = v; return this; }
        public Builder path(String v) { this.path = v; return this; }
        public Builder status(int v) { this.status = v; return this; }
        public Builder bytes(long v) { this.bytes = v; return this; }
        public Builder userAgent(String v) { this.userAgent = v; return this; }
        public Builder raw(String v) { this.raw = v; return this; }

        public Builder attribute(String key, String value) {
            attributes.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public LogRecord build() {
            return new LogRecord(this);
        }
    }
}
