# Changelog

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
