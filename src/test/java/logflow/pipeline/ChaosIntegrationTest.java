package logflow.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import logflow.config.LoadedPipeline;
import logflow.config.PipelineLoader;
import logflow.config.StageRegistry;

/**
 * Chaos / Stress testing with corrupt log fixtures (access-dirty.log and access-empty.log).
 * Verifies that:
 * 1. The pipeline never crashes on malformed data.
 * 2. Exactly the valid records reach the main sink.
 * 3. All corrupt records (truncated, non-UTF8, negative bytes, 1970/2099 dates, 100 KiB lines)
 *    are captured and routed to the dead-letter sink.
 * 4. The RunSummary numbers reconcile exactly (recordsIn == recordsOut + deadLetterCount + totalDropped).
 * 5. Empty log file is handled cleanly with 0 records.
 */
class ChaosIntegrationTest {

    @TempDir
    Path dir;

    private final PipelineLoader loader = new PipelineLoader(StageRegistry.standard());

    @Test
    void chaosDirtyLogProcessesCleanly() throws Exception {
        Path cleanOut = dir.resolve("clean.txt");
        Path deadLetterOut = dir.resolve("dead-letter.txt");

        String yaml = "pipeline:\n"
                + "  name: chaos-test\n"
                + "  source:\n"
                + "    type: file\n"
                + "    params: { path: 'data/access-dirty.log' }\n"
                + "  stages:\n"
                + "    - type: parse\n"
                + "      onError: dead_letter\n"
                + "      params: { format: clf }\n"
                + "    - type: validate\n"
                + "      onError: dead_letter\n"
                + "      params:\n"
                + "        required: [clientIp, status, path, timestamp]\n"
                + "        ranges:\n"
                + "          status: { min: 100, max: 599 }\n"
                + "          bytes: { min: 0 }\n"
                + "        minTimestamp: '2000-01-01T00:00:00Z'\n"
                + "        maxTimestamp: '2030-01-01T00:00:00Z'\n"
                + "        ip: true\n"
                + "        maxRawLength: 65536\n"
                + "  sink:\n"
                + "    type: file\n"
                + "    params: { path: '" + cleanOut.toString().replace('\\', '/') + "' }\n"
                + "  deadLetter:\n"
                + "    type: file\n"
                + "    params: { path: '" + deadLetterOut.toString().replace('\\', '/') + "' }\n";

        LoadedPipeline p = loader.loadYaml(yaml);
        RunSummary summary = p.run();

        assertEquals(11, summary.recordsIn(), "Total input records read");
        assertEquals(3, summary.recordsOut(), "Three valid records must reach the main sink");
        assertEquals(8, summary.deadLetterCount(), "Corrupted records routed to dead-letter");
        assertEquals(0, summary.totalDropped(), "No records dropped via skip");
        assertTrue(summary.isAccountedFor(), "Accounting equation must hold exactly: in == out + dead-letter + dropped");

        // Verify valid records content
        List<String> cleanLines = Files.readAllLines(cleanOut, StandardCharsets.UTF_8);
        assertEquals(3, cleanLines.size());
        assertTrue(cleanLines.get(0).contains("/index.html"));
        assertTrue(cleanLines.get(1).contains("/api/login"));
        assertTrue(cleanLines.get(2).contains("/images/logo.png"));

        // Verify dead-letter content contains diagnostics for all failure modes
        List<String> dlLines = Files.readAllLines(deadLetterOut, StandardCharsets.UTF_8);
        assertEquals(8, dlLines.size());

        String dlCombined = String.join("\n", dlLines);
        assertTrue(dlCombined.contains("stage=parse"));
        assertTrue(dlCombined.contains("stage=validate"));
        assertTrue(dlCombined.contains("1970"));
        assertTrue(dlCombined.contains("2099"));
        assertTrue(dlCombined.contains("Invalid UTF-8"));
        assertTrue(dlCombined.contains("exceeds maximum allowed limit"));
    }

    @Test
    void emptyFileHandledCleanly() throws Exception {
        Path cleanOut = dir.resolve("clean-empty.txt");
        Path deadLetterOut = dir.resolve("dead-letter-empty.txt");

        String yaml = "pipeline:\n"
                + "  name: empty-test\n"
                + "  source:\n"
                + "    type: file\n"
                + "    params: { path: 'data/access-empty.log' }\n"
                + "  stages:\n"
                + "    - type: parse\n"
                + "      onError: dead_letter\n"
                + "  sink:\n"
                + "    type: file\n"
                + "    params: { path: '" + cleanOut.toString().replace('\\', '/') + "' }\n"
                + "  deadLetter:\n"
                + "    type: file\n"
                + "    params: { path: '" + deadLetterOut.toString().replace('\\', '/') + "' }\n";

        LoadedPipeline p = loader.loadYaml(yaml);
        RunSummary summary = p.run();

        assertEquals(0, summary.recordsIn());
        assertEquals(0, summary.recordsOut());
        assertEquals(0, summary.deadLetterCount());
        assertEquals(0, summary.totalDropped());
        assertTrue(summary.isAccountedFor());
    }
}
