package logflow;

import java.nio.file.Paths;

import logflow.io.ConsoleSink;
import logflow.io.FileLineSource;
import logflow.pipeline.Pipeline;
import logflow.stage.LimitStage;

/** Reads arguments, assembles the pipeline, runs it. No business logic here. */
public final class Main {

    private static final int LINE_LIMIT = 5;

    private Main() {}

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: logflow <log-file>");
            System.exit(1);
        }

        Pipeline<String, String> pipeline =
                new Pipeline<String, String>(new FileLineSource(Paths.get(args[0])), new ConsoleSink<String>())
                        .addStage(new LimitStage<String>(LINE_LIMIT));

        pipeline.run();
    }
}
