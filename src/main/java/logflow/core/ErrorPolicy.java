package logflow.core;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy applied when a stage throws a {@link StageException} while processing a record.
 * <p>
 * Supported policies:
 * <ul>
 *   <li>{@code FAIL_FAST}: Aborts execution immediately by throwing a {@code PipelineException}.</li>
 *   <li>{@code SKIP}: Silently drops the record, increments the stage's dropped counter, and continues.</li>
 *   <li>{@code DEAD_LETTER}: Routes the failed record with diagnostic context into a secondary dead-letter sink.</li>
 *   <li>{@code RETRY(n)}: Retries the stage operation up to {@code n} times before failing.</li>
 * </ul>
 */
public final class ErrorPolicy {

    public enum Type {
        FAIL_FAST,
        SKIP,
        DEAD_LETTER,
        RETRY
    }

    private static final Pattern RETRY_PATTERN = Pattern.compile("(?i)^retry(?:\\((\\d+)\\))?$");

    public static final ErrorPolicy FAIL_FAST = new ErrorPolicy(Type.FAIL_FAST, 0);
    public static final ErrorPolicy SKIP = new ErrorPolicy(Type.SKIP, 0);
    public static final ErrorPolicy DEAD_LETTER = new ErrorPolicy(Type.DEAD_LETTER, 0);

    private final Type type;
    private final int retries;

    private ErrorPolicy(Type type, int retries) {
        this.type = Objects.requireNonNull(type, "type");
        this.retries = retries;
    }

    public static ErrorPolicy retry(int attempts) {
        if (attempts <= 0) {
            throw new IllegalArgumentException("retry attempts must be positive: " + attempts);
        }
        return new ErrorPolicy(Type.RETRY, attempts);
    }

    public Type type() {
        return type;
    }

    public int retries() {
        return retries;
    }

    /**
     * Parses an error policy string from configuration (case-insensitive).
     * Accepts: {@code "fail_fast"}, {@code "skip"}, {@code "dead_letter"}, {@code "retry(n)"}.
     */
    public static ErrorPolicy parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return FAIL_FAST;
        }
        String clean = text.trim();
        Matcher m = RETRY_PATTERN.matcher(clean);
        if (m.matches()) {
            String count = m.group(1);
            int n = (count == null || count.isEmpty()) ? 3 : Integer.parseInt(count);
            return retry(n);
        }
        String norm = clean.toLowerCase().replace("-", "_").replace(" ", "_");
        switch (norm) {
            case "fail_fast":
            case "failfast":
                return FAIL_FAST;
            case "skip":
                return SKIP;
            case "dead_letter":
            case "deadletter":
                return DEAD_LETTER;
            default:
                throw new IllegalArgumentException("Unknown error policy '" + text
                        + "'. Allowed: fail_fast, skip, dead_letter, retry(n)");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ErrorPolicy)) return false;
        ErrorPolicy that = (ErrorPolicy) o;
        return retries == that.retries && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, retries);
    }

    @Override
    public String toString() {
        return type == Type.RETRY ? "RETRY(" + retries + ")" : type.name();
    }
}

