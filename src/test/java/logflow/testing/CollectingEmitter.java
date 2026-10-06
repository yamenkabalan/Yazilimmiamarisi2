package logflow.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import logflow.core.Emitter;

/**
 * Test double: an {@link Emitter} that just remembers everything emitted into it.
 * <p>
 * A stage test creates the stage, feeds it a String, and checks what landed here —
 * no files, no source, no sink, no pipeline. Reused by every stage test from now on.
 */
public final class CollectingEmitter<T> implements Emitter<T> {

    private final List<T> items = new ArrayList<>();

    @Override
    public void emit(T item) {
        items.add(item);
    }

    /** Everything emitted so far, in order. */
    public List<T> items() {
        return Collections.unmodifiableList(items);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** The only emitted item; fails if there is not exactly one. */
    public T single() {
        if (items.size() != 1) {
            throw new AssertionError("expected exactly 1 emitted item but got " + items.size() + ": " + items);
        }
        return items.get(0);
    }
}
