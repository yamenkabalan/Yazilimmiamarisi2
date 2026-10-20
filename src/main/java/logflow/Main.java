package logflow;

import java.time.Instant;
import java.util.Map;

import logflow.cli.Options;
import logflow.core.Sink;
import logflow.io.FileLineSource;
import logflow.io.FileSink;
import logflow.io.PrintSink;
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

        //  FileLineSource ─▶ ParserStage ─▶ [filters] ─▶ ProjectionStage ─▶ PrintSink (terminal) | FileSink (--out)
        ParserStage parser = new ParserStage();
        PrintSink<Map<String, String>> printSink = new PrintSink<>();
        FileSink<Map<String, String>> fileSink = o.out != null ? new FileSink<>(o.out) : null;
        Sink<Map<String, String>> sink = fileSink != null ? fileSink : printSink;

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

        if (fileSink != null) {
            System.out.println("Wrote " + fileSink.writtenCount() + " records to " + o.out);
        } else {
            System.out.println("Records: " + printSink.writtenCount());
        }
        System.out.println("Malformed lines skipped: " + parser.malformedCount());
    }
}
