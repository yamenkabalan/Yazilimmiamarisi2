# LogFlow — Increment 2: Typed Records and the First Real Filter

Software Architecture term project.

| | |
|---|---|
| **Student** | Mohamad Yamen Kabalan |
| **Student No** | 2404040356 |
| **Version** | `v2` |

## What it does

`logflow` reads an Apache access log, **parses every line into a typed `LogRecord`**,
and prints every record as a readable one-line summary. Lines that cannot be
parsed are **skipped and counted**; the total is printed at the end.

The pipeline has exactly **one stage**:

```
FileLineSource ──▶ ParserStage ──▶ ConsoleSink
   Source           Stage (pump)       Sink
   (String)       String→LogRecord   prints LogRecord
```

## Requirements

- **JDK** 8 or newer (a JRE is enough to *run* the jar, a JDK is needed to *build*)
- Maven is **not** required — use the included wrapper `mvnw` / `mvnw.cmd`
  (it downloads Maven 3.9.9 on first use)

## Build and test

```bash
./mvnw package          # Linux / macOS / Git Bash
mvnw.cmd package        # Windows cmd / PowerShell
```

This compiles, runs all unit tests, writes the coverage report to
`target/site/jacoco/index.html`, and produces `target/logflow.jar`.

Run only the tests: `./mvnw test`

## Run

```bash
./logflow data/access-small.log          # Linux / macOS / Git Bash
logflow data\access-small.log            # Windows cmd
java -jar target/logflow.jar data/access-small.log
```

Expected output:

```
2026-09-28 05:01:03Z  192.168.1.183    DELETE /index.html  200  36770 B  ua="Mozilla/5.0 (Windows NT 10.0; ...)"
2026-09-28 05:01:44Z  10.0.4.235       PUT    /static/js/app.js  200  30862 B  ua="Mozilla/5.0 (Macintosh; ...)"
...
Malformed lines skipped: 0
```

To see malformed-line counting, use the demo file with 4 broken lines:

```bash
java -jar target/logflow.jar data/access-with-errors.log
# ... 6 records ...
# Malformed lines skipped: 4
```

## Tests

JUnit 5. **20 tests**, all passing. `ParserStageTest` alone has 14 cases, including the
8 required ones: valid line, missing field, bad timestamp, bad status code, empty line,
extra whitespace, quoted user agent with spaces, line with a query string.

Stage tests **never touch the file system**: the test creates the stage, gives it a
`String`, and captures what it emits with the `CollectingEmitter` test double
(`src/test/java/logflow/testing/CollectingEmitter.java`). This works because a stage
only depends on the `Emitter` interface, not on a file, a source or a sink.

## Line coverage (JaCoCo, `./mvnw test`)

| Scope | Covered lines | Line coverage |
|---|---|---|
| `ParserStage` | 58 / 60 | **96.7 %** |
| `LogRecord` (+ Builder) | 36 / 37 | 97.3 % |
| `Pipeline` | 25 / 25 | 100 % |
| `ConsoleSink` | 11 / 13 | 84.6 % |
| **Whole project** | **136 / 162** | **84.0 %** |

Not covered: `Main` and `FileLineSource` (they do I/O — the stage tests deliberately avoid files).

## Project structure

```
src/main/java/logflow/
├── Main.java                  reads args, assembles the pipeline, calls run()
├── core/                      the interfaces (the vocabulary)
│   ├── Record.java
│   ├── Stage.java
│   ├── Emitter.java
│   ├── Source.java
│   ├── Sink.java
│   └── StageException.java
├── model/
│   └── LogRecord.java         immutable domain record (+ Builder, attributes map)
├── io/
│   ├── FileLineSource.java    emits one String per line of a file
│   └── ConsoleSink.java       prints each LogRecord as one readable line
├── stage/
│   └── ParserStage.java       CLF / Combined line → LogRecord; skips & counts bad lines
└── pipeline/
    ├── Pipeline.java
    └── PipelineException.java
src/test/java/logflow/
├── testing/CollectingEmitter.java   reusable test double
├── stage/ParserStageTest.java       14 parser tests
├── model/LogRecordTest.java
├── io/ConsoleSinkTest.java
└── pipeline/PipelineTest.java
data/access-small.log          200-line sample Apache access log
data/access-with-errors.log    10 lines, 4 of them malformed (demo)
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for the design and [CHANGELOG.md](CHANGELOG.md) for what changed.
