# Changelog

## v5 — Error handling: policies, dead-letter channel, validation stage and run summary

### Added
- `src/main/java/logflow/core/ErrorPolicy.java` — four policies: `FAIL_FAST`, `SKIP`, `DEAD_LETTER`, `RETRY(n)`;
  parsed from the YAML `onError` key; validated at configuration time.
- `src/main/java/logflow/model/FailedRecord.java` — diagnostic record routed to the dead-letter channel
  (carries raw input, stage name, exception, and failure timestamp).
- `src/main/java/logflow/pipeline/RunSummary.java` — immutable end-of-run report:
  records in/out, dead-letter count, dropped per stage, duration, and a conservation check
  (`recordsIn == recordsOut + deadLetterCount + totalDropped`).
- `src/main/java/logflow/stage/ValidationStage.java` — declarative domain validator:
  required fields, numeric ranges (status, bytes), timestamp window, IP format, max line length.
  Throws `StageException` on any violation; typically used with `onError: dead_letter`.

### Changed (existing files)
- `src/main/java/logflow/stage/ParserStage.java` — now **throws `StageException`** on malformed
  lines (empty, too long, invalid UTF-8, unparseable CLF) instead of silently counting them.
  The `malformedCount()` counter still accumulates for reporting purposes.
- `src/main/java/logflow/pipeline/Pipeline.java` — dispatches `ErrorPolicy` per stage:
  `FAIL_FAST` re-raises as `PipelineException`, `SKIP` counts and continues, `DEAD_LETTER`
  routes a `FailedRecord` to the secondary sink, `RETRY` retries *n* times.
  Added per-stage `StageNode` (counters, name, policy). `run()` returns `RunSummary`.
  Added optional `deadLetterSink`.
- `src/main/java/logflow/pipeline/PipelineBuilder.java` — `via()` accepts a stage name and
  `ErrorPolicy`; added `deadLetter()` method to set the secondary sink.
- `src/main/java/logflow/pipeline/PipelineException.java` — Javadoc clarified
  (unrecoverable, pipeline-level failure vs. record-level `StageException`).
- `src/main/java/logflow/core/StageException.java` — Javadoc clarified
  (recoverable, record-level failure routed by `ErrorPolicy`).
- `src/main/java/logflow/config/StageRegistry.java` — registered new `validate` stage type.
- `src/main/java/logflow/config/PipelineLoader.java` — reads `onError` / `on_error` /
  `errorPolicy` / `error_policy` per stage; reads optional `deadLetter` / `dead_letter` section;
  auto-creates `out/dead-letter.txt` if any stage uses `DEAD_LETTER` but no explicit sink is given.
- `src/main/java/logflow/config/LoadedPipeline.java` — `run()` now returns `RunSummary`;
  added `deadLetterOutput()`.
- `src/main/java/logflow/config/Params.java` — added `has()`, `number()`, `map()` helpers
  needed by the validate stage factory.
- `src/main/java/logflow/Main.java` — prints `RunSummary`; exits with code 3 on `PipelineException`.
- `configs/error-report.yaml` — added `onError: skip` on parse; added `validate` stage with
  `onError: dead_letter`; added `deadLetter` section (`out/dead-letter.txt`).
- `configs/api-traffic.yaml`, `configs/bandwidth-by-client.yaml` — added `onError: skip` on parse.
- `pom.xml` — version bumped to 5.0.0.
- `ARCHITECTURE.md` — v5 section added.

### Test changes
- `src/test/java/logflow/stage/ParserStageTest.java` — `assertSkipped` now uses
  `assertThrows(StageException.class, …)` (parser throws, not silently skips);
  happy-path test methods declare `throws Exception`.
- `src/test/java/logflow/pipeline/PipelineTest.java` — two new tests: `SKIP` policy skips,
  `FAIL_FAST` policy aborts; renamed `sourceParserSink` → `sourceParserSinkWithSkipPolicy`.
- `src/test/java/logflow/pipeline/PipelineBuilderIntegrationTest.java` — parser stage uses
  `ErrorPolicy.SKIP` so the broken fixture line is tolerated.
- `src/test/java/logflow/config/PipelineLoaderTest.java` — `validConfigBuildsAndRuns` and
  `fileSinkWithEchoWritesTheFileAndReportsBothOutputs` add `onError: skip` on the parse stage;
  `unknownStageType` updated for the new `validate` type in the registry;
  three new tests: `parseStageWithSkipPolicy`, `parseStageWithFailFastAbortsPipeline`,
  `invalidErrorPolicyRejectedAtConfigTime`.
- **Total: 61 tests (was 57); all pass.**

### Not changed
`FileLineSource`, `FileSink`, `TeeSink`, `ConsoleSink`, `LogRecord`, `PredicateFilterStage`,
`ProjectionStage`, `ParserStageTest` happy-path assertions, `FieldCondition`, `FieldConditionTest`,
`core` interfaces (`Source`, `Sink`, `Stage`, `Emitter`, `Record`).

## v4 — Configuration-driven composition and a stage registry

### Added
- `src/main/java/logflow/config/StageRegistry.java` — stage type name → factory, one registration table.
- `src/main/java/logflow/config/StageFactory.java` — builds a stage from its params.
- `src/main/java/logflow/config/PipelineLoader.java` — YAML → checked `Pipeline`; fails fast.
- `src/main/java/logflow/config/Params.java`, `LoadedPipeline.java`, `ConfigException.java`.
- `src/main/java/logflow/stage/FieldCondition.java` — `field op value` filter conditions.
- `configs/error-report.yaml`, `configs/api-traffic.yaml`, `configs/bandwidth-by-client.yaml` —
  each writes to a file in `out/` and (with `echo: true`) also prints in the terminal.
- `src/main/java/logflow/io/TeeSink.java` — sends every record to two sinks (file + terminal).
- Tests: `PipelineLoaderTest` (14), `FieldConditionTest` (3), `TeeSinkTest` (1).

### Changed (existing files)
- `src/main/java/logflow/Main.java` — now only `logflow --config <file.yaml>`.
- `src/main/java/logflow/model/LogRecord.java` — `field(name)` and `FIELDS`: read any field by name.
- `src/main/java/logflow/stage/ProjectionStage.java` — uses `LogRecord.field()`; accepts a list.
- `src/main/java/logflow/io/ConsoleSink.java` — the generic printing sink (was `PrintSink`).
- `pom.xml` — version 4.0.0; SnakeYAML (YAML reader); shade plugin packs it into `logflow.jar`.
- `README.md` (error message examples), `ARCHITECTURE.md` (v4 section + configuration schema).

### Removed
- `src/main/java/logflow/cli/Options.java` (+ test) — command-line filters replaced by config files.
- old `ConsoleSink` that only printed `LogRecord`s (+ test).

### Not changed
`ParserStage`, `PredicateFilterStage`, `FileLineSource`, `FileSink`, `Pipeline`, `PipelineBuilder`, all `core` interfaces.

## v3 — Composition: filtering, projection and a pipeline builder

### Added
- `src/main/java/logflow/stage/PredicateFilterStage.java` — passes a record only if a
  `Predicate<LogRecord>` holds; factories `minStatus`, `pathPrefix`, `between`.
- `src/main/java/logflow/stage/ProjectionStage.java` — reduces each record to the chosen fields.
- `src/main/java/logflow/io/FileSink.java` — writes to a file; `open()`/`close()` manage the writer.
- `src/main/java/logflow/pipeline/PipelineBuilder.java` — fluent `from().via().to()` assembly.
- `src/main/java/logflow/cli/Options.java` — `--out`, `--min-status`, `--path-prefix`,
  `--from`, `--to`, `--fields`.
- Tests: `PredicateFilterStageTest`, `ProjectionStageTest`, `FileSinkTest`, `OptionsTest`,
  `PipelineBuilderIntegrationTest` (+ fixture `src/test/resources/fixture.log`), `Records` helper.
- `src/test/java/logflow/experiment/FilterOrderExperiment.java` — the ordering experiment.
- Filters `--status N` (exact status) and `--method M` (e.g. GET): `PredicateFilterStage.status()` / `method()`.
- `src/main/java/logflow/io/PrintSink.java` — prints records in the terminal; this is the default output,
  `--out FILE` switches to `FileSink`.

### Changed (existing files)
- `src/main/java/logflow/core/Sink.java` — added `open()` / `close()` lifecycle hooks (default no-ops).
- `src/main/java/logflow/pipeline/Pipeline.java` — opens the sink before the run and closes it after (in `finally`).
- `src/main/java/logflow/Main.java` — parses options and assembles the pipeline with `PipelineBuilder`.
- `pom.xml` — version 3.0.0; JaCoCo 0.8.15 (works on JDK 8 – 25).
- `README.md`, `ARCHITECTURE.md` (v3 section + ordering experiment), `logflow`, `logflow.cmd`.

### Not changed
`ParserStage`, `LogRecord`, `FileLineSource`, `ConsoleSink`, `Stage`, `Emitter`, `Source`.

## v2 — Typed records and the first real filter

The pipeline now carries a domain model (`LogRecord`) instead of raw strings.

### Added
- `src/main/java/logflow/model/LogRecord.java` — immutable domain record with a Builder
  and an extensible `attributes` map.
- `src/main/java/logflow/stage/ParserStage.java` — parses Common / Combined Log Format
  lines; malformed lines are skipped and counted (proper handling comes in week 5).
- `src/test/java/...` — JUnit 5 test suite (20 tests) and the reusable `CollectingEmitter` test double.
- `data/access-with-errors.log` — demo input containing malformed lines.
- `CHANGELOG.md`, Maven wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`).

### Changed (existing files)
- `src/main/java/logflow/Main.java` — pipeline is now `FileLineSource → ParserStage → ConsoleSink`
  (one stage); prints the malformed-line total at the end.
- `src/main/java/logflow/io/ConsoleSink.java` — now a `Sink<LogRecord>` that prints each record
  as one readable line.
- `pom.xml` — version 2.0.0; JUnit 5, Surefire and JaCoCo (coverage) added.
- `README.md`, `ARCHITECTURE.md` — updated for v2.
- `logflow.cmd` — fixed the usage comment.

### Removed
- `src/main/java/logflow/stage/LimitStage.java` (and its test) — the pipeline is kept to a single
  stage, as requested; all records are printed.

### Not changed
`FileLineSource`, `Pipeline`, and all `core` interfaces — the parser was inserted between
the source and the sink without modifying them.

## v1 — Skeleton pipeline
- Core interfaces, `FileLineSource`, `ConsoleSink`, `Pipeline`, `Main`, `LimitStage(5)`.
