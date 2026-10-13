package logflow.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import logflow.core.Sink;
import logflow.core.Source;
import logflow.core.Stage;

/**
 * Fluent way to assemble a pipeline:
 * <pre>
 * PipelineBuilder.from(source).via(stageA).via(stageB).to(sink)
 * </pre>
 * The type parameter {@code C} is the type flowing at the current end of the chain, so the
 * compiler rejects a wrong order — e.g. a {@code LogRecord} filter before the parser.
 *
 * @param <S> type produced by the source
 * @param <C> type produced by the last stage added so far
 */
public final class PipelineBuilder<S, C> {

    private final Source<S> source;
    private final List<Stage<?, ?>> stages;

    private PipelineBuilder(Source<S> source, List<Stage<?, ?>> stages) {
        this.source = source;
        this.stages = stages;
    }

    public static <S> PipelineBuilder<S, S> from(Source<S> source) {
        return new PipelineBuilder<>(Objects.requireNonNull(source, "source"), new ArrayList<>());
    }

    /** Appends a stage whose input matches the current output type. */
    public <N> PipelineBuilder<S, N> via(Stage<? super C, N> stage) {
        List<Stage<?, ?>> next = new ArrayList<>(stages);
        next.add(Objects.requireNonNull(stage, "stage"));
        return new PipelineBuilder<>(source, next);
    }

    /** Finishes the chain with a sink and returns the runnable pipeline. */
    public Pipeline<S, C> to(Sink<C> sink) {
        Pipeline<S, C> pipeline = new Pipeline<>(source, sink);
        for (Stage<?, ?> stage : stages) {
            pipeline.addStage(stage);
        }
        return pipeline;
    }
}
