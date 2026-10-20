package logflow.config;

import java.util.List;

import logflow.core.Stage;
import logflow.pipeline.Pipeline;
import logflow.stage.ParserStage;

/** A pipeline built from a config file, ready to run, plus what to report afterwards. */
public final class LoadedPipeline {

    private final String name;
    private final Pipeline<String, Object> pipeline;
    private final List<Stage<?, ?>> stages;
    private final PipelineLoader.CountingSink sink;
    private final String output;

    LoadedPipeline(String name, Pipeline<String, Object> pipeline, List<Stage<?, ?>> stages,
                   PipelineLoader.CountingSink sink, String output) {
        this.name = name;
        this.pipeline = pipeline;
        this.stages = stages;
        this.sink = sink;
        this.output = output;
    }

    public void run() {
        pipeline.run();
    }

    public String name() { return name; }

    /** Where the records went: a file path, or "console". */
    public String output() { return output; }

    /** Number of stages in the pipeline. */
    public int stageCount() { return stages.size(); }

    /** Records that reached the sink in the last run. */
    public long recordCount() { return sink.count; }

    /** Lines the parser skipped because they were malformed. */
    public long malformedCount() {
        long n = 0;
        for (Stage<?, ?> stage : stages) {
            if (stage instanceof ParserStage) {
                n += ((ParserStage) stage).malformedCount();
            }
        }
        return n;
    }
}
