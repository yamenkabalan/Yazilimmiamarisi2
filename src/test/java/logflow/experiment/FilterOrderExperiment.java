package logflow.experiment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import logflow.core.Sink;
import logflow.core.Source;
import logflow.core.Stage;
import logflow.model.LogRecord;
import logflow.pipeline.PipelineBuilder;
import logflow.stage.ParserStage;
import logflow.stage.PredicateFilterStage;
import logflow.stage.ProjectionStage;

/**
 * Week-3 ordering experiment (results are written up in ARCHITECTURE.md).
 * Runs the same data through the pipeline with the stages in different orders and reports
 * the output (must be identical), how many times each stage did work, and the run time.
 * <p>
 * Run after {@code mvnw test-compile}:
 * <pre>java -cp target/classes;target/test-classes logflow.experiment.FilterOrderExperiment</pre>
 */
public final class FilterOrderExperiment {

    private static final int REPEAT = 5_000;   // 200 lines x 5000 = 1,000,000 lines
    private static final String[] FIELDS = {"timestamp", "clientIp", "status", "path"};

    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("data/access-small.log"), StandardCharsets.UTF_8);
        Source<String> source = out -> {
            for (int i = 0; i < REPEAT; i++) {
                for (String line : lines) {
                    out.emit(line);
                }
            }
        };
        System.out.printf("Input: %,d lines%n%n", (long) lines.size() * REPEAT);
        System.out.printf("%-40s %9s %12s %12s %12s %8s%n",
                "order", "output", "status-test", "prefix-test", "projections", "ms");

        for (int round = 0; round < 2; round++) {   // round 0 = JIT warm-up, round 1 is reported
            boolean print = round == 1;
            run("A  parse > status>=400 > /api > project", source, print, true, false);
            run("B  parse > /api > status>=400 > project", source, print, false, false);
            run("C  parse > project > status>=400 > /api", source, print, true, true);
        }
    }

    private static void run(String name, Source<String> source, boolean print,
                            boolean statusFirst, boolean projectFirst) {
        AtomicLong statusTests = new AtomicLong();
        AtomicLong prefixTests = new AtomicLong();
        AtomicLong projections = new AtomicLong();
        Predicate<LogRecord> status = r -> { statusTests.incrementAndGet(); return r.status() >= 400; };
        Predicate<LogRecord> prefix = r -> { prefixTests.incrementAndGet(); return r.path().startsWith("/api"); };
        Stage<LogRecord, Map<String, String>> project = counting(new ProjectionStage(FIELDS), projections);
        ResultSink sink = new ResultSink();

        long start = System.nanoTime();
        if (projectFirst) {
            // After projection only Map<String,String> is left: a LogRecord filter no longer fits,
            // so the filters have to be rewritten against the projected fields.
            PipelineBuilder.from(source).via(new ParserStage()).via(project)
                    .via(mapFilter(m -> { statusTests.incrementAndGet(); return Integer.parseInt(m.get("status")) >= 400; }))
                    .via(mapFilter(m -> { prefixTests.incrementAndGet(); return m.get("path").startsWith("/api"); }))
                    .to(sink).run();
        } else {
            PipelineBuilder.from(source).via(new ParserStage())
                    .via(new PredicateFilterStage(statusFirst ? status : prefix))
                    .via(new PredicateFilterStage(statusFirst ? prefix : status))
                    .via(project)
                    .to(sink).run();
        }
        long ms = (System.nanoTime() - start) / 1_000_000;

        if (print) {
            System.out.printf("%-40s %,9d %,12d %,12d %,12d %8d   hash=%08x%n", name, sink.count,
                    statusTests.get(), prefixTests.get(), projections.get(), ms, sink.hash);
        }
    }

    private static <I, O> Stage<I, O> counting(Stage<I, O> stage, AtomicLong calls) {
        return (in, out) -> { calls.incrementAndGet(); stage.process(in, out); };
    }

    private static Stage<Map<String, String>, Map<String, String>> mapFilter(Predicate<Map<String, String>> p) {
        return (in, out) -> { if (p.test(in)) out.emit(in); };
    }

    /** Counts outputs and folds them into an order-sensitive hash, to prove the outputs are equal. */
    private static final class ResultSink implements Sink<Map<String, String>> {
        long count;
        int hash = 17;

        @Override
        public void consume(Map<String, String> item) {
            count++;
            hash = 31 * hash + Arrays.asList(item.values().toArray()).hashCode();
        }
    }

    private FilterOrderExperiment() {}
}
