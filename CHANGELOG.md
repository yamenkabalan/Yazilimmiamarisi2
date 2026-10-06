# Changelog

## v2 — Typed records and the first real filter

The pipeline now carries a domain model (`LogRecord`) instead of raw strings.

### Added
- `src/main/java/logflow/model/LogRecord.java` — immutable domain record with a Builder
  and an extensible `attributes` map.
- `src/main/java/logflow/stage/ParserStage.java` — parses Common / Combined Log Format
  lines; malformed lines are skipped and counted (proper handling comes in week 5).
- `src/main/java/logflow/io/LogRecordFormatter.java` — readable one-line format for a `LogRecord`.
- `src/test/java/...` — JUnit 5 test suite (26 tests) and the reusable `CollectingEmitter` test double.
- `data/access-with-errors.log` — demo input containing malformed lines.
- `CHANGELOG.md`, Maven wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`).

### Changed (existing files)
- `src/main/java/logflow/Main.java` — inserts `ParserStage` into the pipeline and prints
  the parsed / malformed totals at the end.
- `src/main/java/logflow/io/ConsoleSink.java` — takes a formatter function; `forLogRecords()`
  prints a `LogRecord` as one readable line.
- `pom.xml` — version 2.0.0; JUnit 5, Surefire and JaCoCo (coverage) added.
- `README.md`, `ARCHITECTURE.md` — updated for v2.
- `logflow.cmd` — fixed the usage comment.

### Not changed
`FileLineSource`, `LimitStage`, `Pipeline`, and all `core` interfaces — the parser was
inserted between its neighbours without modifying them.

## v1 — Skeleton pipeline
- Core interfaces, `FileLineSource`, `ConsoleSink`, `Pipeline`, `Main`, `LimitStage(5)`.
