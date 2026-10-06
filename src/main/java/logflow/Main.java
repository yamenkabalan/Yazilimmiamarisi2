package logflow;

import java.nio.file.Paths;

import logflow.io.ConsoleSink;
import logflow.io.FileLineSource;
import logflow.model.LogRecord;
import logflow.pipeline.Pipeline;
import logflow.stage.ParserStage;

/** Reads arguments, assembles the pipeline, runs it. No business logic here. */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: logflow <log-file>");
            System.exit(1);
        }

        //  FileLineSource ─▶ ParserStage ─▶ ConsoleSink
        ParserStage parser = new ParserStage();
        new Pipeline<String, LogRecord>(new FileLineSource(Paths.get(args[0])), new ConsoleSink())
                .addStage(parser)
                .run();

        System.out.println("Malformed lines skipped: " + parser.malformedCount());
    }
}
