package logflow.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import logflow.io.FileLineSource;
import logflow.io.FileSink;
import logflow.stage.ParserStage;
import logflow.stage.PredicateFilterStage;
import logflow.stage.ProjectionStage;

/** End-to-end: fixture file → parse → filter → project → output file, then assert the file content. */
class PipelineBuilderIntegrationTest {

    private static final List<String> EXPECTED = Arrays.asList(
            "{timestamp=2026-09-28T05:01:00Z, clientIp=10.0.0.2, status=404, path=/api/orders/42}",
            "{timestamp=2026-09-28T05:03:00Z, clientIp=10.0.0.4, status=500, path=/api/products}");

    @TempDir
    Path dir;

    private Path fixture() throws Exception {
        return Paths.get(getClass().getResource("/fixture.log").toURI());
    }

    @Test
    void parseFilterProjectWrite() throws Exception {
        Path out = dir.resolve("result.log");
        ParserStage parser = new ParserStage();

        Pipeline<String, Map<String, String>> p = PipelineBuilder.from(new FileLineSource(fixture()))
                .via(parser)
                .via(PredicateFilterStage.minStatus(400))
                .via(PredicateFilterStage.pathPrefix("/api"))
                .via(new ProjectionStage("timestamp", "clientIp", "status", "path"))
                .to(new FileSink<>(out));
        p.run();

        assertEquals(EXPECTED, Files.readAllLines(out, StandardCharsets.UTF_8));
        assertEquals(1, parser.malformedCount());
    }

    @Test
    void filterOrderDoesNotChangeTheResult() throws Exception {
        Path statusFirst = dir.resolve("a.log");
        Path prefixFirst = dir.resolve("b.log");

        PipelineBuilder.from(new FileLineSource(fixture()))
                .via(new ParserStage())
                .via(PredicateFilterStage.minStatus(400))
                .via(PredicateFilterStage.pathPrefix("/api"))
                .via(new ProjectionStage("timestamp", "clientIp", "status", "path"))
                .to(new FileSink<>(statusFirst)).run();
        PipelineBuilder.from(new FileLineSource(fixture()))
                .via(new ParserStage())
                .via(PredicateFilterStage.pathPrefix("/api"))
                .via(PredicateFilterStage.minStatus(400))
                .via(new ProjectionStage("timestamp", "clientIp", "status", "path"))
                .to(new FileSink<>(prefixFirst)).run();

        assertEquals(EXPECTED, Files.readAllLines(statusFirst, StandardCharsets.UTF_8));
        assertEquals(EXPECTED, Files.readAllLines(prefixFirst, StandardCharsets.UTF_8));
    }
}
