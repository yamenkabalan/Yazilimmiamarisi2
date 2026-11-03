package logflow.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import logflow.core.ErrorPolicy;
import logflow.core.Sink;
import logflow.core.Source;
import logflow.core.Stage;
import logflow.io.ConsoleSink;
import logflow.io.FileLineSource;
import logflow.io.FileSink;
import logflow.io.TeeSink;
import logflow.model.FailedRecord;
import logflow.pipeline.Pipeline;

/**
 * Reads a YAML config and builds the pipeline it describes: finds every stage in the
 * {@link StageRegistry}, checks all parameters, error policies, dead-letter destination,
 * and stage order, and only then returns.
 * Any mistake throws a {@link ConfigException} before a single log line is read.
 */
public final class PipelineLoader {

    private static final Map<String, Function<Params, Source<String>>> SOURCES = new LinkedHashMap<>();
    private static final Map<String, Function<Params, Sink<Object>>> SINKS = new LinkedHashMap<>();

    static {
        SOURCES.put("file", p -> {
            Path path = Paths.get(p.string("path"));
            if (!Files.isRegularFile(path)) {
                throw p.error("input file not found: " + path);
            }
            return new FileLineSource(path);
        });
        SINKS.put("file", p -> {
            Sink<Object> file = new FileSink<>(Paths.get(p.string("path")));
            // echo: true → also print every record in the terminal
            return p.bool("echo", false) ? new TeeSink<>(file, new ConsoleSink<>()) : file;
        });
        SINKS.put("console", p -> new ConsoleSink<>());
    }

    private final StageRegistry registry;

    public PipelineLoader(StageRegistry registry) {
        this.registry = registry;
    }

    public LoadedPipeline load(Path configFile) {
        try {
            return loadYaml(new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ConfigException("cannot read config file: " + configFile);
        }
    }

    private static final class ConfiguredStage {
        final String name;
        final Stage<?, ?> stage;
        final ErrorPolicy errorPolicy;

        ConfiguredStage(String name, Stage<?, ?> stage, ErrorPolicy errorPolicy) {
            this.name = name;
            this.stage = stage;
            this.errorPolicy = errorPolicy;
        }
    }

    public LoadedPipeline loadYaml(String yaml) {
        Object root;
        try {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        } catch (YAMLException e) {
            throw new ConfigException("the file is not valid YAML: " + e.getMessage());
        }
        Map<?, ?> pipeline = map(map(root, "the file").get("pipeline"), "'pipeline'");
        String name = text(pipeline, "name", "pipeline");

        // source
        Map<?, ?> sourceConfig = map(pipeline.get("source"), "'source'");
        String sourceType = text(sourceConfig, "type", "source");
        Function<Params, Source<String>> sourceFactory = SOURCES.get(sourceType);
        if (sourceFactory == null) {
            throw new ConfigException("source: unknown type '" + sourceType + "'. Known types: " + SOURCES.keySet());
        }
        Params sourceParams = new Params("source (" + sourceType + ")", sourceConfig.get("params"));
        Source<String> source = sourceFactory.apply(sourceParams);
        sourceParams.checkNoUnknown();

        // stages
        Object stagesConfig = pipeline.get("stages");
        if (!(stagesConfig instanceof List) || ((List<?>) stagesConfig).isEmpty()) {
            throw new ConfigException("'stages' is empty — a pipeline needs at least one stage, e.g. '- type: parse'");
        }
        List<ConfiguredStage> stageNodes = new ArrayList<>();
        List<Stage<?, ?>> rawStages = new ArrayList<>();
        Class<?> flowing = String.class;   // the type the source produces
        int number = 0;
        for (Object item : (List<?>) stagesConfig) {
            number++;
            String where = "stage " + number;
            Map<?, ?> stageConfig = map(item, where);
            String type = text(stageConfig, "type", where);
            where = "stage " + number + " (" + type + ")";

            StageRegistry.Entry entry = registry.find(type);
            if (entry == null) {
                throw new ConfigException(where + ": unknown stage type '" + type + "'. Known types: " + registry.types());
            }
            if (!entry.input.isAssignableFrom(flowing)) {
                throw new ConfigException(where + ": needs " + entry.input.getSimpleName() + " input but gets "
                        + flowing.getSimpleName() + ". Check the order of the stages (e.g. 'parse' must come first).");
            }

            // Error policy (default: FAIL_FAST)
            String onErrorStr = null;
            if (stageConfig.containsKey("onError")) {
                onErrorStr = String.valueOf(stageConfig.get("onError"));
            } else if (stageConfig.containsKey("on_error")) {
                onErrorStr = String.valueOf(stageConfig.get("on_error"));
            } else if (stageConfig.containsKey("errorPolicy")) {
                onErrorStr = String.valueOf(stageConfig.get("errorPolicy"));
            } else if (stageConfig.containsKey("error_policy")) {
                onErrorStr = String.valueOf(stageConfig.get("error_policy"));
            }

            ErrorPolicy errorPolicy;
            try {
                errorPolicy = ErrorPolicy.parse(onErrorStr);
            } catch (IllegalArgumentException e) {
                throw new ConfigException(where + ": " + e.getMessage());
            }

            Params params = new Params(where, stageConfig.get("params"));
            Stage<?, ?> stage;
            try {
                stage = entry.factory.create(params);
            } catch (IllegalArgumentException e) {
                throw params.error(e.getMessage());
            }
            params.checkNoUnknown();
            rawStages.add(stage);
            stageNodes.add(new ConfiguredStage(type, stage, errorPolicy));
            flowing = entry.output;
        }

        // sink
        Map<?, ?> sinkConfig = map(pipeline.get("sink"), "'sink'");
        String sinkType = text(sinkConfig, "type", "sink");
        Function<Params, Sink<Object>> sinkFactory = SINKS.get(sinkType);
        if (sinkFactory == null) {
            throw new ConfigException("sink: unknown type '" + sinkType + "'. Known types: " + SINKS.keySet());
        }
        Params sinkParams = new Params("sink (" + sinkType + ")", sinkConfig.get("params"));
        CountingSink sink = new CountingSink(sinkFactory.apply(sinkParams));
        sinkParams.checkNoUnknown();
        String output = !sinkType.equals("file") ? "console"
                : sinkParams.string("path") + (sinkParams.bool("echo", false) ? " + console" : "");

        // dead-letter sink (optional in config, defaults to file if any stage has DEAD_LETTER policy)
        Sink<FailedRecord> deadLetterSink = null;
        String deadLetterOutput = "none";
        Object dlConfigObj = pipeline.get("deadLetter") != null
                ? pipeline.get("deadLetter")
                : pipeline.get("dead_letter");

        if (dlConfigObj instanceof Map) {
            Map<?, ?> dlMap = (Map<?, ?>) dlConfigObj;
            String dlType = text(dlMap, "type", "deadLetter");
            Params dlParams = new Params("deadLetter (" + dlType + ")", dlMap.get("params"));
            if ("file".equals(dlType)) {
                Path dlPath = Paths.get(dlParams.string("path"));
                FileSink<FailedRecord> fileSink = new FileSink<>(dlPath);
                boolean echo = dlParams.bool("echo", false);
                deadLetterSink = echo ? new TeeSink<>(fileSink, new ConsoleSink<>()) : fileSink;
                deadLetterOutput = dlParams.string("path") + (echo ? " + console" : "");
            } else if ("console".equals(dlType)) {
                deadLetterSink = new ConsoleSink<>();
                deadLetterOutput = "console";
            } else {
                throw new ConfigException("deadLetter: unknown type '" + dlType + "'. Known types: [file, console]");
            }
            dlParams.checkNoUnknown();
        } else {
            // Check if any stage has DEAD_LETTER configured
            boolean hasDeadLetterStage = false;
            for (ConfiguredStage cs : stageNodes) {
                if (cs.errorPolicy.type() == ErrorPolicy.Type.DEAD_LETTER) {
                    hasDeadLetterStage = true;
                    break;
                }
            }
            if (hasDeadLetterStage) {
                deadLetterSink = new FileSink<>(Paths.get("out/dead-letter.txt"));
                deadLetterOutput = "out/dead-letter.txt";
            }
        }

        Pipeline<String, Object> result = new Pipeline<>(name, source, sink);
        if (deadLetterSink != null) {
            result.setDeadLetterSink(deadLetterSink);
        }
        for (ConfiguredStage cs : stageNodes) {
            result.addStage(cs.name, cs.stage, cs.errorPolicy);
        }
        return new LoadedPipeline(name, result, rawStages, sink, output, deadLetterOutput);
    }

    private static Map<?, ?> map(Object value, String what) {
        if (!(value instanceof Map)) {
            throw new ConfigException(what + " is missing or is not a section (key: value ...)");
        }
        return (Map<?, ?>) value;
    }

    private static String text(Map<?, ?> section, String key, String where) {
        Object value = section.get(key);
        if (!(value instanceof String)) {
            throw new ConfigException(where + ": missing '" + key + "'");
        }
        return (String) value;
    }

    /** Counts what reaches the sink, so the program can report it. */
    static final class CountingSink implements Sink<Object> {
        private final Sink<Object> target;
        long count;

        CountingSink(Sink<Object> target) {
            this.target = target;
        }

        @Override public void open() { count = 0; target.open(); }
        @Override public void consume(Object item) { count++; target.consume(item); }
        @Override public void close() { target.close(); }
    }
}
