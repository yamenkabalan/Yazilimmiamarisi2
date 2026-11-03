package logflow.model;

import java.time.Instant;
import java.util.Objects;

import logflow.core.Record;

/**
 * Diagnostic record emitted to the secondary dead-letter channel when a record fails
 * during stage processing.
 * <p>
 * Carries the raw input, the stage where the failure occurred, the underlying exception,
 * and the timestamp of the failure.
 */
public final class FailedRecord implements Record {

    private final String rawInput;
    private final String stageName;
    private final Throwable exception;
    private final Instant timestamp;

    public FailedRecord(String rawInput, String stageName, Throwable exception, Instant timestamp) {
        this.rawInput = rawInput != null ? rawInput : "";
        this.stageName = Objects.requireNonNull(stageName, "stageName");
        this.exception = Objects.requireNonNull(exception, "exception");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }

    public String rawInput() {
        return rawInput;
    }

    public String stageName() {
        return stageName;
    }

    public Throwable exception() {
        return exception;
    }

    public Instant timestamp() {
        return timestamp;
    }

    @Override
    public String raw() {
        return rawInput;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FailedRecord)) return false;
        FailedRecord that = (FailedRecord) o;
        return Objects.equals(rawInput, that.rawInput)
                && Objects.equals(stageName, that.stageName)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(exception.getMessage(), that.exception.getMessage());
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawInput, stageName, timestamp, exception.getMessage());
    }

    @Override
    public String toString() {
        return "FailedRecord[timestamp=" + timestamp
                + ", stage=" + stageName
                + ", error=" + exception.getMessage()
                + ", raw=" + rawInput + "]";
    }
}

