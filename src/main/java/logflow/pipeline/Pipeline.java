package logflow.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import logflow.core.Emitter;
import logflow.core.Sink;
import logflow.core.Source;
import logflow.core.Stage;
import logflow.core.StageException;

/**
 * An ordered list of stages between one source and one sink.
 * {@link #run()} pushes every record from the source, through each stage in
 * order, into the sink.
 *
 * @param <S> type produced by the source
 * @param <T> type consumed by the sink
 */
public final class Pipeline<S, T> {

    private final Source<S> source;
    private final List<Stage<Object, Object>> stages = new ArrayList<>();
    private final Sink<T> sink;

    public Pipeline(Source<S> source, Sink<T> sink) {
        this.source = Objects.requireNonNull(source, "source");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Appends a stage; stages run in the order they are added. */
    @SuppressWarnings("unchecked")
    public Pipeline<S, T> addStage(Stage<?, ?> stage) {
        stages.add((Stage<Object, Object>) Objects.requireNonNull(stage, "stage"));
        return this;
    }

    @SuppressWarnings("unchecked")
    public void run() {
        for (Stage<Object, Object> stage : stages) {
            stage.open();
        }
        try {
            Emitter<S> head = (Emitter<S>) (Emitter<?>) chainFrom(0);
            source.produce(head);
        } finally {
            for (int i = stages.size() - 1; i >= 0; i--) {
                stages.get(i).close();
            }
        }
    }

    /** Builds the emitter that feeds stage {@code index}; past the last stage it feeds the sink. */
    @SuppressWarnings("unchecked")
    private Emitter<Object> chainFrom(int index) {
        if (index == stages.size()) {
            return item -> sink.consume((T) item);
        }
        Stage<Object, Object> stage = stages.get(index);
        Emitter<Object> next = chainFrom(index + 1);
        return item -> {
            try {
                stage.process(item, next);
            } catch (StageException e) {
                throw new PipelineException("Stage " + index + " failed", e);
            }
        };
    }
}
