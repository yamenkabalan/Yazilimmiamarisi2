package logflow.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import logflow.core.ErrorPolicy;
import logflow.core.Sink;
import logflow.core.Source;
import logflow.core.Stage;
import logflow.model.FailedRecord;

/**
 * Fluent way to assemble a pipeline:
 * <pre>
 * PipelineBuilder.from(source).via(stageA).via(stageB, ErrorPolicy.DEAD_LETTER).to(sink)
 * </pre>
 *
 * @param <S> type produced by the source
 * @param <C> type produced by the last stage added so far
 */
public final class PipelineBuilder<S, C> {

    public static final class StageSpec {
        final String name;
        final Stage<?, ?> stage;
        final ErrorPolicy errorPolicy;

        StageSpec(String name, Stage<?, ?> stage, ErrorPolicy errorPolicy) {
            this.name = name;
            this.stage = stage;
            this.errorPolicy = errorPolicy;
        }
    }

    private final Source<S> source;
    private final List<StageSpec> stages;
    private final Sink<FailedRecord> deadLetterSink;

    private PipelineBuilder(Source<S> source, List<StageSpec> stages, Sink<FailedRecord> deadLetterSink) {
        this.source = source;
        this.stages = stages;
        this.deadLetterSink = deadLetterSink;
    }

    public static <S> PipelineBuilder<S, S> from(Source<S> source) {
        return new PipelineBuilder<>(Objects.requireNonNull(source, "source"), new ArrayList<>(), null);
    }

    public PipelineBuilder<S, C> deadLetter(Sink<FailedRecord> deadLetterSink) {
        return new PipelineBuilder<>(source, stages, deadLetterSink);
    }

    /** Appends a stage with default FAIL_FAST policy. */
    public <N> PipelineBuilder<S, N> via(Stage<? super C, N> stage) {
        return via("stage-" + (stages.size() + 1), stage, ErrorPolicy.FAIL_FAST);
    }

    public <N> PipelineBuilder<S, N> via(Stage<? super C, N> stage, ErrorPolicy policy) {
        return via("stage-" + (stages.size() + 1), stage, policy);
    }

    public <N> PipelineBuilder<S, N> via(String name, Stage<? super C, N> stage, ErrorPolicy policy) {
        List<StageSpec> next = new ArrayList<>(stages);
        next.add(new StageSpec(name, Objects.requireNonNull(stage, "stage"),
                policy != null ? policy : ErrorPolicy.FAIL_FAST));
        return new PipelineBuilder<>(source, next, deadLetterSink);
    }

    /** Finishes the chain with a sink and returns the runnable pipeline. */
    public Pipeline<S, C> to(Sink<C> sink) {
        Pipeline<S, C> pipeline = new Pipeline<>(source, sink);
        if (deadLetterSink != null) {
            pipeline.setDeadLetterSink(deadLetterSink);
        }
        for (StageSpec spec : stages) {
            pipeline.addStage(spec.name, spec.stage, spec.errorPolicy);
        }
        return pipeline;
    }
}
