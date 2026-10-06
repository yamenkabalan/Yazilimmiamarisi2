package logflow;

import java.nio.file.Paths;

import logflow.io.ConsoleSink;
import logflow.io.FileLineSource;
import logflow.model.LogRecord;
import logflow.pipeline.Pipeline;
import logflow.stage.LimitStage;
import logflow.stage.ParserStage;

/** Reads arguments, assembles the pipeline, runs it. No business logic here. */
public final class Main {

    private static final int RECORD_LIMIT = 5;

    private Main() {}

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: logflow <log-file>");
            System.exit(1);
        }

        ParserStage parser = new ParserStage();

        //  FileLineSource ─▶ ParserStage ─▶ LimitStage(5) ─▶ ConsoleSink
        Pipeline<String, LogRecord> pipeline =
                new Pipeline<String, LogRecord>(new FileLineSource(Paths.get(args[0])),
                        ConsoleSink.forLogRecords(System.out))
                        .addStage(parser)
                        .addStage(new LimitStage<LogRecord>(RECORD_LIMIT));

        pipeline.run();

        System.out.println("Parsed lines: " + parser.parsedCount()
                + ", malformed lines skipped: " + parser.malformedCount());
    }
}
