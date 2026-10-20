package logflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipelineLoaderTest {

    @TempDir
    Path dir;

    private final PipelineLoader loader = new PipelineLoader(StageRegistry.standard());

    private String fixture() throws Exception {
        return Paths.get(getClass().getResource("/fixture.log").toURI()).toString().replace('\\', '/');
    }

    /** A config with the given stages; source = fixture, sink = a file in the temp dir. */
    private String config(String stages) throws Exception {
        return "pipeline:\n"
                + "  name: test\n"
                + "  source:\n"
                + "    type: file\n"
                + "    params: { path: '" + fixture() + "' }\n"
                + "  stages:\n" + stages
                + "  sink:\n"
                + "    type: file\n"
                + "    params: { path: '" + dir.resolve("out.txt").toString().replace('\\', '/') + "' }\n";
    }

    private String errorOf(String yaml) {
        return assertThrows(ConfigException.class, () -> loader.loadYaml(yaml)).getMessage();
    }

    // ---- the 5 required cases ---------------------------------------------------------

    @Test
    void validConfigBuildsAndRuns() throws Exception {
        LoadedPipeline p = loader.loadYaml(config(
                "    - type: parse\n"
                + "      params: { format: clf }\n"
                + "    - type: filter\n"
                + "      params: { field: status, op: '>=', value: 400 }\n"
                + "    - type: project\n"
                + "      params: { fields: [clientIp, status] }\n"));

        assertEquals("test", p.name());
        assertEquals(3, p.stageCount());
        p.run();

        assertEquals(Arrays.asList(
                "{clientIp=10.0.0.2, status=404}",
                "{clientIp=10.0.0.3, status=401}",
                "{clientIp=10.0.0.4, status=500}",
                "{clientIp=10.0.0.6, status=503}"),
                Files.readAllLines(dir.resolve("out.txt"), StandardCharsets.UTF_8));
        assertEquals(4, p.recordCount());
        assertEquals(1, p.malformedCount());
    }

    @Test
    void unknownStageType() throws Exception {
        String msg = errorOf(config("    - type: parse\n    - type: filtr\n"));
        assertEquals("stage 2 (filtr): unknown stage type 'filtr'. Known types: [parse, filter, project]", msg);
    }

    @Test
    void missingParameter() throws Exception {
        String msg = errorOf(config("    - type: parse\n    - type: filter\n      params: { field: status, value: 400 }\n"));
        assertEquals("stage 2 (filter): missing required parameter 'op'", msg);
    }

    @Test
    void wrongTypeParameter() throws Exception {
        String msg = errorOf(config(
                "    - type: parse\n    - type: filter\n      params: { field: status, op: '>=', value: four-hundred }\n"));
        assertEquals("stage 2 (filter): op '>=' needs a number as value, got 'four-hundred'", msg);

        msg = errorOf(config("    - type: parse\n    - type: project\n      params: { fields: timestamp }\n"));
        assertEquals("stage 2 (project): parameter 'fields' must be a non-empty list like [a, b], got: timestamp", msg);
    }

    @Test
    void emptyPipeline() throws Exception {
        String msg = errorOf(config("    []\n"));
        assertTrue(msg.startsWith("'stages' is empty"), msg);
    }

    // ---- more configuration mistakes -------------------------------------------------

    @Test
    void wrongStageOrder() throws Exception {
        String msg = errorOf(config("    - type: filter\n      params: { field: status, op: '>=', value: 400 }\n    - type: parse\n"));
        assertTrue(msg.startsWith("stage 1 (filter): needs LogRecord input but gets String"), msg);
    }

    @Test
    void misspelledParameter() throws Exception {
        String msg = errorOf(config("    - type: parse\n    - type: project\n      params: { fields: [status], feilds: [path] }\n"));
        assertTrue(msg.startsWith("stage 2 (project): unknown parameter 'feilds'"), msg);
    }

    @Test
    void unknownField() throws Exception {
        String msg = errorOf(config("    - type: parse\n    - type: project\n      params: { fields: [ip] }\n"));
        assertTrue(msg.startsWith("stage 2 (project): unknown field 'ip'"), msg);
    }

    @Test
    void missingInputFile() {
        String msg = errorOf("pipeline:\n  name: x\n  source: { type: file, params: { path: no/such.log } }\n"
                + "  stages: [ { type: parse } ]\n  sink: { type: console }\n");
        assertEquals("source (file): input file not found: " + Paths.get("no/such.log"), msg);
    }

    @Test
    void unknownSinkType() throws Exception {
        String msg = errorOf(config("    - type: parse\n").replace("type: file\n    params: { path: '" + dir.resolve("out.txt").toString().replace('\\', '/') + "' }", "type: printer"));
        assertEquals("sink: unknown type 'printer'. Known types: [file, console]", msg);
    }

    @Test
    void notYaml() {
        assertTrue(errorOf("pipeline: [ unclosed").startsWith("the file is not valid YAML"));
    }

    @Test
    void theShippedConfigFilesAllLoad() throws Exception {
        int count = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(Paths.get("configs"), "*.yaml")) {
            for (Path file : files) {
                loader.load(file);   // throws ConfigException if a file is broken
                count++;
            }
        }
        assertEquals(3, count);
    }
}
