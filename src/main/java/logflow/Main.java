package logflow;

import java.time.Instant;
import java.util.Map;

import logflow.cli.Options;
import logflow.io.FileLineSource;
import logflow.io.FileSink;
import logflow.model.LogRecord;
import logflow.pipeline.Pipeline;
import logflow.pipeline.PipelineBuilder;
import logflow.stage.ParserStage;
import logflow.stage.PredicateFilterStage;
import logflow.stage.ProjectionStage;

/** Reads arguments, assembles the pipeline, runs it. No business logic here. */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        Options o;
        try {
            o = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(Options.USAGE);
            System.exit(1);
            return;
        }

        //  FileLineSource ─▶ ParserStage ─▶ [filters] ─▶ ProjectionStage ─▶ FileSink
        ParserStage parser = new ParserStage();
        FileSink<Map<String, String>> sink = new FileSink<>(o.out);

        PipelineBuilder<String, LogRecord> chain = PipelineBuilder.from(new FileLineSource(o.input)).via(parser);
        if (o.status != null) {
            chain = chain.via(PredicateFilterStage.status(o.status));
        }
        if (o.method != null) {
            chain = chain.via(PredicateFilterStage.method(o.method));
        }
        if (o.minStatus != null) {
            chain = chain.via(PredicateFilterStage.minStatus(o.minStatus));
        }
        if (o.pathPrefix != null) {
            chain = chain.via(PredicateFilterStage.pathPrefix(o.pathPrefix));
        }
        if (o.from != null || o.to != null) {
            chain = chain.via(PredicateFilterStage.between(
                    o.from != null ? o.from : Instant.MIN, o.to != null ? o.to : Instant.MAX));
        }
        Pipeline<String, Map<String, String>> pipeline =
                chain.via(new ProjectionStage(o.fields)).to(sink);

        pipeline.run();

        System.out.println("Wrote " + sink.writtenCount() + " records to " + o.out);
        System.out.println("Malformed lines skipped: " + parser.malformedCount());
    }
}
