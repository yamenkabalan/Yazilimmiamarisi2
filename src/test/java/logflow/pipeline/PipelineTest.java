package logflow.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import logflow.core.ErrorPolicy;
import logflow.core.Source;
import logflow.core.Stage;
import logflow.core.StageException;
import logflow.model.LogRecord;
import logflow.stage.ParserStage;

/** Wires the parser between an in-memory source and a list sink — still no files. */
class PipelineTest {

    private static final String GOOD =
            "10.0.0.1 - - [28/Sep/2026:08:01:03 +0300] \"GET /a HTTP/1.1\" 200 10 \"-\" \"curl/8\"";

    @Test
    void sourceParserSinkWithSkipPolicy() {
        // v5: malformed lines throw StageException; with SKIP policy they are silently dropped
        Source<String> source = out -> {
            for (String s : Arrays.asList(GOOD, "bad", GOOD, "")) {
                out.emit(s);
            }
        };
        List<LogRecord> sink = new ArrayList<>();
        ParserStage parser = new ParserStage();

        new Pipeline<String, LogRecord>(source, sink::add)
                .addStage("parse", parser, ErrorPolicy.SKIP)
                .run();

        assertEquals(2, sink.size());
        assertEquals(2, parser.malformedCount());
    }

    @Test
    void sourceParserSinkWithFailFastPolicy() {
        // v5: with FAIL_FAST, the first bad line aborts the pipeline
        Source<String> source = out -> {
            for (String s : Arrays.asList(GOOD, "bad", GOOD)) {
                out.emit(s);
            }
        };
        List<LogRecord> sink = new ArrayList<>();
        ParserStage parser = new ParserStage();

        Pipeline<String, LogRecord> p = new Pipeline<String, LogRecord>(source, sink::add)
                .addStage("parse", parser, ErrorPolicy.FAIL_FAST);

        assertThrows(PipelineException.class, p::run);
    }

    @Test
    void stageFailureIsWrapped() {
        Stage<String, String> failing = (in, out) -> {
            throw new StageException("boom");
        };
        Pipeline<String, String> p = new Pipeline<String, String>(out -> out.emit("x"), item -> {})
                .addStage(failing);

        assertThrows(PipelineException.class, p::run);
    }
}
