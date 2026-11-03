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

import logflow.core.ErrorPolicy;
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

        // v5: parser uses SKIP so the one broken line in fixture.log is silently skipped (not FAIL_FAST)
        Pipeline<String, Map<String, String>> p = PipelineBuilder.from(new FileLineSource(fixture()))
                .via("parse",  parser,                                    ErrorPolicy.SKIP)
                .via("filter", PredicateFilterStage.minStatus(400),       ErrorPolicy.FAIL_FAST)
                .via("filter", PredicateFilterStage.pathPrefix("/api"),   ErrorPolicy.FAIL_FAST)
                .via("project", new ProjectionStage("timestamp", "clientIp", "status", "path"), ErrorPolicy.FAIL_FAST)
                .to(new FileSink<>(out));
        p.run();

        assertEquals(EXPECTED, Files.readAllLines(out, StandardCharsets.UTF_8));
        assertEquals(1, parser.malformedCount());
    }

    @Test
    void filterOrderDoesNotChangeTheResult() throws Exception {
        Path statusFirst = dir.resolve("a.log");
        Path prefixFirst = dir.resolve("b.log");

        // v5: parser uses SKIP policy so broken lines don't abort the pipeline
        PipelineBuilder.from(new FileLineSource(fixture()))
                .via("parse",  new ParserStage(),                         ErrorPolicy.SKIP)
                .via("filter", PredicateFilterStage.minStatus(400),       ErrorPolicy.FAIL_FAST)
                .via("filter", PredicateFilterStage.pathPrefix("/api"),   ErrorPolicy.FAIL_FAST)
                .via("project", new ProjectionStage("timestamp", "clientIp", "status", "path"), ErrorPolicy.FAIL_FAST)
                .to(new FileSink<>(statusFirst)).run();

        PipelineBuilder.from(new FileLineSource(fixture()))
                .via("parse",  new ParserStage(),                         ErrorPolicy.SKIP)
                .via("filter", PredicateFilterStage.pathPrefix("/api"),   ErrorPolicy.FAIL_FAST)
                .via("filter", PredicateFilterStage.minStatus(400),       ErrorPolicy.FAIL_FAST)
                .via("project", new ProjectionStage("timestamp", "clientIp", "status", "path"), ErrorPolicy.FAIL_FAST)
                .to(new FileSink<>(prefixFirst)).run();

        assertEquals(EXPECTED, Files.readAllLines(statusFirst, StandardCharsets.UTF_8));
        assertEquals(EXPECTED, Files.readAllLines(prefixFirst, StandardCharsets.UTF_8));
    }
}
