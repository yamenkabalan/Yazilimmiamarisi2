package logflow;

import java.nio.file.Paths;

import logflow.io.ConsoleSink;
import logflow.io.FileLineSource;
import logflow.pipeline.Pipeline;

/** Reads arguments, assembles the pipeline, runs it. No business logic here. */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: logflow <log-file>");
            System.exit(1);
        }

        Pipeline<String, String> pipeline =
                new Pipeline<>(new FileLineSource(Paths.get(args[0])), new ConsoleSink<String>());

        pipeline.run();
    }
}
