package logflow.stage;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;

import logflow.core.Emitter;
import logflow.core.Stage;
import logflow.model.LogRecord;

/** Passes a record on only if the condition holds; otherwise drops it (0 outputs). */
public final class PredicateFilterStage implements Stage<LogRecord, LogRecord> {

    private final Predicate<LogRecord> condition;

    public PredicateFilterStage(Predicate<LogRecord> condition) {
        this.condition = Objects.requireNonNull(condition, "condition");
    }

    @Override
    public void process(LogRecord record, Emitter<LogRecord> out) {
        if (condition.test(record)) {
            out.emit(record);
        }
    }

    public static PredicateFilterStage minStatus(int min) {
        return new PredicateFilterStage(r -> r.status() >= min);
    }

    public static PredicateFilterStage status(int status) {
        return new PredicateFilterStage(r -> r.status() == status);
    }

    public static PredicateFilterStage method(String method) {
        return new PredicateFilterStage(r -> r.method().equalsIgnoreCase(method));
    }

    public static PredicateFilterStage pathPrefix(String prefix) {
        return new PredicateFilterStage(r -> r.path().startsWith(prefix));
    }

    /** Keeps records with {@code from <= timestamp < to}. */
    public static PredicateFilterStage between(Instant from, Instant to) {
        return new PredicateFilterStage(r -> !r.timestamp().isBefore(from) && r.timestamp().isBefore(to));
    }
}
