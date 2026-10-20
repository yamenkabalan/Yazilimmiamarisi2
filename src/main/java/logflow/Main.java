package logflow;

import java.nio.file.Paths;

import logflow.config.ConfigException;
import logflow.config.LoadedPipeline;
import logflow.config.PipelineLoader;
import logflow.config.StageRegistry;

/**
 * Entry point: {@code logflow --config configs/error-report.yaml}.
 * The config file decides the whole pipeline — a new pipeline needs a new YAML file, not a recompile.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        if (args.length != 2 || !args[0].equals("--config")) {
            System.err.println("Usage: logflow --config <file.yaml>");
            System.exit(1);
        }

        LoadedPipeline pipeline;
        try {
            pipeline = new PipelineLoader(StageRegistry.standard()).load(Paths.get(args[1]));
        } catch (ConfigException e) {
            // configuration error: stop before any data is read
            System.err.println("Config error in " + args[1] + ": " + e.getMessage());
            System.exit(2);
            return;
        }

        pipeline.run();

        System.out.println("Pipeline '" + pipeline.name() + "': " + pipeline.recordCount()
                + " records -> " + pipeline.output()
                + " (malformed lines skipped: " + pipeline.malformedCount() + ")");
    }
}
