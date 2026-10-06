# Changelog

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
