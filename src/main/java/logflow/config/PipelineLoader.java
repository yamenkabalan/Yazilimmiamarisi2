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

import logflow.core.Sink;
import logflow.core.Source;
import logflow.core.Stage;
import logflow.io.ConsoleSink;
import logflow.io.FileLineSource;
import logflow.io.FileSink;
import logflow.pipeline.Pipeline;

/**
 * Reads a YAML config and builds the pipeline it describes: finds every stage in the
 * {@link StageRegistry}, checks all parameters and the stage order, and only then returns.
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
        SINKS.put("file", p -> new FileSink<>(Paths.get(p.string("path"))));
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
        List<Stage<?, ?>> stages = new ArrayList<>();
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
            Params params = new Params(where, stageConfig.get("params"));
            try {
                stages.add(entry.factory.create(params));
            } catch (IllegalArgumentException e) {
                throw params.error(e.getMessage());
            }
            params.checkNoUnknown();
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
        String output = sinkType.equals("file") ? sinkParams.string("path") : "console";

        Pipeline<String, Object> result = new Pipeline<>(source, sink);
        for (Stage<?, ?> stage : stages) {
            result.addStage(stage);
        }
        return new LoadedPipeline(name, result, stages, sink, output);
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
