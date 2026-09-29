package logflow.stage;

import logflow.core.Emitter;
import logflow.core.Stage;

/** Passes through only the first {@code limit} items and drops the rest. */
public final class LimitStage<T> implements Stage<T, T> {

    private final int limit;
    private int passed;

    public LimitStage(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be >= 0");
        }
        this.limit = limit;
    }

    @Override
    public void open() {
        passed = 0;
    }

    @Override
    public void process(T input, Emitter<T> out) {
        if (passed < limit) {
            passed++;
            out.emit(input);
        }
    }
}
