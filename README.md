# LogFlow — Increment 3: Composition — Filtering, Projection and a Pipeline Builder

Software Architecture term project.

| | |
|---|---|
| **Student** | Mohamad Yamen Kabalan |
| **Student No** | 2404040356 |
| **Version** | `v3` |

## What it does

`logflow` is a **configurable command-line log filter**. It reads an Apache access log,
parses every line into a `LogRecord`, keeps only the records that match the filters given on
the command line, reduces each record to the chosen fields, and prints the result in the terminal (or writes it to a file with `--out`).

```
FileLineSource ─▶ ParserStage ─▶ PredicateFilterStage(s) ─▶ ProjectionStage ─▶ FileSink
   reads             parses          keeps / drops             picks fields       writes
```

The pipeline is assembled with the fluent `PipelineBuilder`:

```java
Pipeline<String, Map<String, String>> p = PipelineBuilder.from(new FileLineSource(in))
        .via(new ParserStage())
        .via(new PredicateFilterStage(r -> r.status() >= 400))
        .via(new ProjectionStage("timestamp", "clientIp", "status", "path"))
        .to(new FileSink<>(out));
p.run();
```

## Requirements

- **JDK** 8 or newer to build (a JRE is enough to run the jar)
- Maven is **not** required — use the included wrapper `mvnw` / `mvnw.cmd`

## Build and test

```bash
mvnw.cmd package        # Windows
./mvnw package          # Linux / macOS / Git Bash
```

Compiles, runs all tests, writes the coverage report to `target/site/jacoco/index.html`
and produces `target/logflow.jar`.

## Run

```
logflow <log-file> [--out FILE] [--status N] [--min-status N] [--method M] [--path-prefix P]
                   [--from ISO-TIME] [--to ISO-TIME] [--fields a,b,c]
```

| Option | Meaning | Default |
|---|---|---|
| `--out FILE` | write to this file instead of the terminal | print to the terminal |
| `--status N` | keep records with exactly `status == N` (e.g. `400`) | no filter |
| `--min-status N` | keep records with `status >= N` | no filter |
| `--method M` | keep records with this HTTP method (e.g. `GET`, case-insensitive) | no filter |
| `--path-prefix P` | keep records whose path starts with `P` | no filter |
| `--from T` / `--to T` | keep records with `from <= timestamp < to` (e.g. `2026-09-28T05:00:00Z`) | no filter |
| `--fields a,b,c` | fields to keep: `timestamp, clientIp, method, path, status, bytes, userAgent` or any attribute (`protocol`, `query`, `referer`) | `timestamp,clientIp,method,path,status,bytes` |

Example — API errors only, four fields:

```bash
logflow data\access-small.log --min-status 400 --path-prefix /api --fields timestamp,clientIp,status,path
```

```
{timestamp=2026-09-28T05:21:42Z, clientIp=85.105.37.186, status=403, path=/api/products}
{timestamp=2026-09-28T05:25:39Z, clientIp=85.105.60.124, status=403, path=/api/orders/42}
...
Records: 8
Malformed lines skipped: 0
```

The same, written to a file (`FileSink`) instead of the terminal (`PrintSink`):

```bash
logflow data\access-small.log --min-status 400 --path-prefix /api --out out\result.log
```

More filter examples:

```bash
logflow dataccess-small.log --status 400                  # only status 400
logflow dataccess-small.log --method GET                  # only GET requests
logflow dataccess-small.log --status 404 --method GET     # GET requests that returned 404
```

## Ordering experiment

```bash
mvnw.cmd test-compile
java -cp "target/classes;target/test-classes" logflow.experiment.FilterOrderExperiment
```

Runs 1,000,000 lines through the pipeline with the stages in different orders.
Results and discussion: [ARCHITECTURE.md → Ordering experiment](ARCHITECTURE.md#ordering-experiment-v3).

## Tests

JUnit 5, **44 tests**, all passing.

| Test class | What it checks |
|---|---|
| `ParserStageTest` (14) | the 8 required parser cases + 6 more |
| `PredicateFilterStageTest` (7) | exact status, `status >= 400`, method, path prefix, time range, any lambda |
| `ProjectionStageTest` (5) | field subset and order, attributes, immutability |
| `FileSinkTest` (5) | writing, **output lost without `close()`**, directories, misuse |
| `PipelineBuilderIntegrationTest` (2) | fixture file → parse → filter → project → file, and the same output in both filter orders |
| `OptionsTest` (4) | command-line parsing and error messages |
| `PipelineTest`, `LogRecordTest`, `ConsoleSinkTest` (6) | earlier increments |

Stage tests do not touch the file system — they use the `CollectingEmitter` test double.
Only `FileSinkTest` and the integration test use files (a JUnit temp directory), because
writing files is exactly what they test.

## Line coverage (JaCoCo)

| Scope | Covered lines | Line coverage |
|---|---|---|
| `ParserStage` | 58 / 60 | 96.7 % |
| `PredicateFilterStage` | 11 / 11 | 100 % |
| `ProjectionStage` | 20 / 20 | 100 % |
| `PipelineBuilder`, `Pipeline` | 40 / 40 | 100 % |
| `Options` | 31 / 31 | 100 % |
| `FileSink` | 19 / 25 | 76.0 % (I/O error branches) |
| **Whole project** | **244 / 286** | **85.3 %** |

Not covered: `Main` (only assembles and runs).

## Project structure

```
src/main/java/logflow/
├── Main.java                  reads options, assembles the pipeline, calls run()
├── cli/
│   └── Options.java           command-line arguments → values
├── core/                      the interfaces (Record, Stage, Emitter, Source, Sink, StageException)
├── model/
│   └── LogRecord.java         immutable domain record (+ Builder, attributes map)
├── io/
│   ├── FileLineSource.java    emits one String per line of a file
│   ├── FileSink.java          writes one line per item; open()/close() manage the writer
│   ├── PrintSink.java         prints one line per item to the terminal
│   └── ConsoleSink.java       prints a LogRecord as one readable line (v2)
├── stage/
│   ├── ParserStage.java       CLF / Combined line → LogRecord; skips & counts bad lines
│   ├── PredicateFilterStage.java  keeps a record only if a Predicate<LogRecord> holds
│   └── ProjectionStage.java   LogRecord → only the chosen fields
└── pipeline/
    ├── Pipeline.java
    ├── PipelineBuilder.java   from(...).via(...).via(...).to(...)
    └── PipelineException.java
src/test/java/logflow/...      tests, CollectingEmitter, FilterOrderExperiment
src/test/resources/fixture.log 7-line fixture for the integration test
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for the design and [CHANGELOG.md](CHANGELOG.md) for what changed.
