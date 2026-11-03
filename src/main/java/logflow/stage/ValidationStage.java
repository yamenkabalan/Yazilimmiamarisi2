package logflow.stage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import logflow.core.Emitter;
import logflow.core.Stage;
import logflow.core.StageException;
import logflow.model.LogRecord;

/**
 * Validates domain {@link LogRecord}s against declarative rules.
 * <p>
 * Supported rules:
 * <ul>
 *   <li><b>Required fields:</b> fields that must not be null, empty, or "-".</li>
 *   <li><b>Numeric ranges:</b> min and max bounds for {@code status} (e.g. [100, 599])
 *       and {@code bytes} (e.g. min 0).</li>
 *   <li><b>Timestamp consistency:</b> ensures the event timestamp falls within an acceptable
 *       epoch window (e.g. between 2000 and 2030, rejecting corrupted 1970 or 2099 dates).</li>
 *   <li><b>IP format:</b> validates client IP addresses conform to standard IPv4 or IPv6 syntax.</li>
 *   <li><b>Max record length:</b> catches oversized lines (e.g. 100 KB payloads).</li>
 * </ul>
 * Any validation failure throws a {@link StageException}, which is routed according to the
 * stage's configured error policy.
 */
public final class ValidationStage implements Stage<LogRecord, LogRecord> {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^[0-9a-fA-F:]+$");

    private final List<String> requiredFields;
    private final Map<String, LongRange> ranges;
    private final Instant minTimestamp;
    private final Instant maxTimestamp;
    private final boolean checkIp;
    private final int maxRawLength;

    public static final class LongRange {
        public final Long min;
        public final Long max;

        public LongRange(Long min, Long max) {
            this.min = min;
            this.max = max;
        }

        public boolean contains(long val) {
            if (min != null && val < min) return false;
            if (max != null && val > max) return false;
            return true;
        }

        @Override
        public String toString() {
            return "[" + (min != null ? min : "-inf") + ", " + (max != null ? max : "+inf") + "]";
        }
    }

    private ValidationStage(Builder b) {
        this.requiredFields = Collections.unmodifiableList(new ArrayList<>(b.requiredFields));
        this.ranges = Collections.unmodifiableMap(new LinkedHashMap<>(b.ranges));
        this.minTimestamp = b.minTimestamp;
        this.maxTimestamp = b.maxTimestamp;
        this.checkIp = b.checkIp;
        this.maxRawLength = b.maxRawLength;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void process(LogRecord record, Emitter<LogRecord> out) throws StageException {
        Objects.requireNonNull(record, "record");

        // 1. Max raw length check (e.g. 100 KB line)
        if (maxRawLength > 0 && record.raw() != null && record.raw().length() > maxRawLength) {
            throw new StageException("Record raw length (" + record.raw().length()
                    + ") exceeds maximum allowed limit (" + maxRawLength + ")");
        }

        // 2. Required fields
        for (String field : requiredFields) {
            String val = record.field(field);
            if (val == null || val.trim().isEmpty() || "-".equals(val.trim())) {
                throw new StageException("Validation failed: required field '" + field + "' is missing or empty");
            }
        }

        // 3. Numeric ranges (status, bytes, etc.)
        LongRange statusRange = ranges.get("status");
        if (statusRange != null && !statusRange.contains(record.status())) {
            throw new StageException("Validation failed: status " + record.status()
                    + " is outside allowed range " + statusRange);
        }

        LongRange bytesRange = ranges.get("bytes");
        if (bytesRange != null && !bytesRange.contains(record.bytes())) {
            throw new StageException("Validation failed: bytes " + record.bytes()
                    + " is outside allowed range " + bytesRange);
        }

        // 4. Timestamp consistency
        Instant t = record.timestamp();
        if (t != null) {
            if (minTimestamp != null && t.isBefore(minTimestamp)) {
                throw new StageException("Validation failed: timestamp " + t
                        + " is before minimum consistent timestamp " + minTimestamp);
            }
            if (maxTimestamp != null && t.isAfter(maxTimestamp)) {
                throw new StageException("Validation failed: timestamp " + t
                        + " is after maximum consistent timestamp " + maxTimestamp);
            }
        }

        // 5. IP format
        if (checkIp) {
            String ip = record.clientIp();
            if (!isValidIp(ip)) {
                throw new StageException("Validation failed: invalid IP format '" + ip + "'");
            }
        }

        // Valid: pass to downstream stages
        out.emit(record);
    }

    private static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        if (IPV4_PATTERN.matcher(ip).matches()) return true;
        if (ip.contains(":") && IPV6_PATTERN.matcher(ip).matches()) return true;
        return false;
    }

    public List<String> requiredFields() { return requiredFields; }
    public Map<String, LongRange> ranges() { return ranges; }
    public Instant minTimestamp() { return minTimestamp; }
    public Instant maxTimestamp() { return maxTimestamp; }
    public boolean checkIp() { return checkIp; }
    public int maxRawLength() { return maxRawLength; }

    public static final class Builder {
        private final List<String> requiredFields = new ArrayList<>();
        private final Map<String, LongRange> ranges = new LinkedHashMap<>();
        private Instant minTimestamp;
        private Instant maxTimestamp;
        private boolean checkIp = false;
        private int maxRawLength = 65536;

        public Builder require(String field) {
            requiredFields.add(field);
            return this;
        }

        public Builder required(List<String> fields) {
            requiredFields.addAll(fields);
            return this;
        }

        public Builder range(String field, Long min, Long max) {
            ranges.put(field, new LongRange(min, max));
            return this;
        }

        public Builder minStatus(int min) {
            LongRange current = ranges.get("status");
            ranges.put("status", new LongRange((long) min, current != null ? current.max : null));
            return this;
        }

        public Builder maxStatus(int max) {
            LongRange current = ranges.get("status");
            ranges.put("status", new LongRange(current != null ? current.min : null, (long) max));
            return this;
        }

        public Builder minBytes(long min) {
            LongRange current = ranges.get("bytes");
            ranges.put("bytes", new LongRange(min, current != null ? current.max : null));
            return this;
        }

        public Builder maxBytes(long max) {
            LongRange current = ranges.get("bytes");
            ranges.put("bytes", new LongRange(current != null ? current.min : null, max));
            return this;
        }

        public Builder minTimestamp(Instant min) {
            this.minTimestamp = min;
            return this;
        }

        public Builder maxTimestamp(Instant max) {
            this.maxTimestamp = max;
            return this;
        }

        public Builder checkIp(boolean check) {
            this.checkIp = check;
            return this;
        }

        public Builder maxRawLength(int length) {
            this.maxRawLength = length;
            return this;
        }

        public ValidationStage build() {
            return new ValidationStage(this);
        }
    }
}

