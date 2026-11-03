package logflow.pipeline;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import logflow.core.Emitter;
import logflow.core.ErrorPolicy;
import logflow.core.Record;
import logflow.core.Sink;
import logflow.core.Source;
import logflow.core.Stage;
import logflow.core.StageException;
import logflow.model.FailedRecord;

/**
 * An ordered list of stages between one source, one primary sink, and an optional
 * secondary dead-letter sink.
 * <p>
 * {@link #run()} pushes every record from the source, through each stage in
 * order, into the sink. Stages apply their configured {@link ErrorPolicy}:
 * <ul>
 *   <li>FAIL_FAST: halts the pipeline immediately with a {@link PipelineException}.</li>
 *   <li>SKIP: drops the offending record, increments the stage's dropped counter, and continues.</li>
 *   <li>DEAD_LETTER: sends a {@link FailedRecord} to the secondary dead-letter channel.</li>
 *   <li>RETRY: retries the operation up to {@code n} times.</li>
 * </ul>
 *
 * @param <S> type produced by the source
 * @param <T> type consumed by the sink
 */
public final class Pipeline<S, T> {

    public static final class StageNode {
        private final String name;
        private final Stage<Object, Object> stage;
        private final ErrorPolicy errorPolicy;
        private long recordsIn;
        private long recordsOut;
        private long recordsDropped;
        private long recordsDeadLettered;
        private long errorCount;

        @SuppressWarnings("unchecked")
        public StageNode(String name, Stage<?, ?> stage, ErrorPolicy errorPolicy) {
            this.name = Objects.requireNonNull(name, "name");
            this.stage = (Stage<Object, Object>) Objects.requireNonNull(stage, "stage");
            this.errorPolicy = Objects.requireNonNull(errorPolicy, "errorPolicy");
        }

        public String name() { return name; }
        public Stage<Object, Object> stage() { return stage; }
        public ErrorPolicy errorPolicy() { return errorPolicy; }
        public long recordsIn() { return recordsIn; }
        public long recordsOut() { return recordsOut; }
        public long recordsDropped() { return recordsDropped; }
        public long recordsDeadLettered() { return recordsDeadLettered; }
        public long errorCount() { return errorCount; }

        public void reset() {
            recordsIn = 0;
            recordsOut = 0;
            recordsDropped = 0;
            recordsDeadLettered = 0;
            errorCount = 0;
        }
    }

    private final String name;
    private final Source<S> source;
    private final List<StageNode> stages = new ArrayList<>();
    private final Sink<T> sink;
    private Sink<FailedRecord> deadLetterSink;

    private long recordsIn;
    private long recordsOut;
    private long deadLetterCount;
    private RunSummary summary;

    public Pipeline(Source<S> source, Sink<T> sink) {
        this("pipeline", source, sink);
    }

    public Pipeline(String name, Source<S> source, Sink<T> sink) {
        this.name = name != null ? name : "pipeline";
        this.source = Objects.requireNonNull(source, "source");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    public Pipeline<S, T> setDeadLetterSink(Sink<FailedRecord> deadLetterSink) {
        this.deadLetterSink = deadLetterSink;
        return this;
    }

    public Sink<FailedRecord> deadLetterSink() {
        return deadLetterSink;
    }

    /** Appends a stage with default FAIL_FAST error policy. */
    public Pipeline<S, T> addStage(Stage<?, ?> stage) {
        return addStage("stage-" + (stages.size() + 1), stage, ErrorPolicy.FAIL_FAST);
    }

    public Pipeline<S, T> addStage(String name, Stage<?, ?> stage) {
        return addStage(name, stage, ErrorPolicy.FAIL_FAST);
    }

    public Pipeline<S, T> addStage(Stage<?, ?> stage, ErrorPolicy errorPolicy) {
        return addStage("stage-" + (stages.size() + 1), stage, errorPolicy);
    }

    public Pipeline<S, T> addStage(String name, Stage<?, ?> stage, ErrorPolicy errorPolicy) {
        stages.add(new StageNode(name, stage, errorPolicy));
        return this;
    }

    public List<StageNode> stages() {
        return stages;
    }

    public RunSummary summary() {
        return summary;
    }

    @SuppressWarnings("unchecked")
    public RunSummary run() {
        long startNanos = System.nanoTime();
        recordsIn = 0;
        recordsOut = 0;
        deadLetterCount = 0;

        sink.open();
        try {
            if (deadLetterSink != null) {
                deadLetterSink.open();
            }
            try {
                for (StageNode node : stages) {
                    node.stage.open();
                    node.reset();
                }
                try {
                    Emitter<S> head = (Emitter<S>) (Emitter<?>) chainFrom(0);
                    source.produce(item -> {
                        recordsIn++;
                        head.emit(item);
                    });
                } finally {
                    for (int i = stages.size() - 1; i >= 0; i--) {
                        stages.get(i).stage.close();
                    }
                }
            } finally {
                if (deadLetterSink != null) {
                    deadLetterSink.close();
                }
            }
        } finally {
            sink.close();
        }

        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        this.summary = buildSummary(durationMillis);
        return this.summary;
    }

    /** Builds the emitter that feeds stage {@code index}; past the last stage it feeds the sink. */
    @SuppressWarnings("unchecked")
    private Emitter<Object> chainFrom(int index) {
        if (index == stages.size()) {
            return item -> {
                recordsOut++;
                sink.consume((T) item);
            };
        }

        StageNode node = stages.get(index);
        Stage<Object, Object> stage = node.stage;
        Emitter<Object> next = chainFrom(index + 1);

        return item -> {
            node.recordsIn++;
            int attempts = 0;
            int maxAttempts = node.errorPolicy.type() == ErrorPolicy.Type.RETRY
                    ? 1 + node.errorPolicy.retries()
                    : 1;

            while (true) {
                attempts++;
                try {
                    int[] emittedCount = new int[1];
                    stage.process(item, outItem -> {
                        emittedCount[0]++;
                        node.recordsOut++;
                        next.emit(outItem);
                    });
                    if (emittedCount[0] == 0) {
                        // Filter stage deliberately dropped this record (predicate evaluated to false)
                        node.recordsDropped++;
                    }
                    return;
                } catch (StageException e) {
                    node.errorCount++;
                    if (node.errorPolicy.type() == ErrorPolicy.Type.RETRY && attempts < maxAttempts) {
                        continue;
                    }

                    switch (node.errorPolicy.type()) {
                        case FAIL_FAST:
                        case RETRY:
                            throw new PipelineException("Stage '" + node.name + "' failed fast: " + e.getMessage(), e);

                        case SKIP:
                            node.recordsDropped++;
                            return;

                        case DEAD_LETTER:
                            node.recordsDeadLettered++;
                            deadLetterCount++;
                            String raw = extractRaw(item);
                            FailedRecord failedRecord = new FailedRecord(raw, node.name, e, Instant.now());
                            if (deadLetterSink != null) {
                                deadLetterSink.consume(failedRecord);
                            }
                            return;
                    }
                }
            }
        };
    }

    private static String extractRaw(Object item) {
        if (item == null) {
            return "";
        }
        if (item instanceof Record) {
            return ((Record) item).raw();
        }
        return String.valueOf(item);
    }

    private RunSummary buildSummary(long durationMillis) {
        Map<String, Long> dropped = new LinkedHashMap<>();
        Map<String, Long> deadLetter = new LinkedHashMap<>();
        Map<String, Long> errors = new LinkedHashMap<>();

        for (StageNode node : stages) {
            dropped.put(node.name, node.recordsDropped);
            deadLetter.put(node.name, node.recordsDeadLettered);
            errors.put(node.name, node.errorCount);
        }

        return new RunSummary(name, recordsIn, recordsOut, deadLetterCount, dropped, deadLetter, errors, durationMillis);
    }
}
