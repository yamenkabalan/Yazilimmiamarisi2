# LogFlow — Increment 5: Error Handling, Validation, and Dead-Letter Channel

Software Architecture term project.

| | |
|---|---|
| **Student** | Mohamad Yamen Kabalan |
| **Student No** | 2404040356 |
| **Version** | `v5` |

## What it does

LogFlow treats error handling as an architectural routing decision rather than ad-hoc try/catch blocks:
- **`ErrorPolicy` per stage**: `FAIL_FAST` (halt), `SKIP` (drop & count), `DEAD_LETTER` (route with context), `RETRY(n)`.
- **`ValidationStage`**: declarative domain validation (required fields, numeric ranges, timestamp window, IP format, max length).
- **Dead-letter channel**: routes `FailedRecord` (raw line, stage, exception, timestamp) to a secondary sink (`out/dead-letter.txt`).
- **`RunSummary`**: prints execution accounting reconciling input records with outputs, dead-letter records, and drops.

```bash
logflow --config configs/chaos-dirty.yaml
```

```
============================================================
Pipeline Run Summary: chaos-dirty
============================================================
Duration:               44 ms
Records in:             11
Records out:            3
Dead-letter records:    8
Dropped per stage:
  - parse:             0
  - validate:          0
Total dropped:          0
Accounting check:       EXACT MATCH (11 in == 3 out + 8 dead-letter + 0 dropped)
============================================================
```

## The configurations

| Config | Input | What it demonstrates | Output |
|---|---|---|---|
| `configs/chaos-dirty.yaml` | `data/access-dirty.log` | **Chaos test**: 3 valid records, 8 corrupted records (truncated, non-UTF8, negative bytes, 1970/2099 dates, 100 KiB line) routed to dead-letter | `out/clean.txt` + `out/dead-letter.txt` |
| `configs/error-report.yaml` | `data/access-small.log` | Failed requests (`status >= 400`) validated and projected | `out/errors.txt` + terminal |
| `configs/api-traffic.yaml` | `data/access-small.log` | `/api` requests projected | `out/api-traffic.txt` + terminal |
| `configs/bandwidth-by-client.yaml` | `data/access-small.log` | Responses with body (`bytes > 0`) | `out/bandwidth.txt` + terminal |

### `configs/chaos-dirty.yaml` example:

```yaml
pipeline:
  name: chaos-dirty
  source:
    type: file
    params: { path: data/access-dirty.log }
  stages:
    - type: parse
      onError: dead_letter
      params: { format: clf }
    - type: validate
      onError: dead_letter
      params:
        required: [clientIp, status, path, timestamp]
        ranges:
          status: { min: 100, max: 599 }
          bytes: { min: 0 }
        minTimestamp: "2000-01-01T00:00:00Z"
        maxTimestamp: "2030-01-01T00:00:00Z"
        ip: true
        maxRawLength: 65536
  sink:
    type: file
    params: { path: out/clean.txt, echo: true }
  deadLetter:
    type: file
    params: { path: out/dead-letter.txt, echo: true }
```

## Requirements, build, tests

- JDK 8 or newer to build; Maven via the wrapper `mvnw.cmd`.
- `mvnw.cmd clean package` — compiles, runs **63 tests**, writes the coverage report to
  `target/site/jacoco/index.html`, and builds `target/logflow.jar`.

| Test class | What it checks |
|---|---|
| `ChaosIntegrationTest` (2) | `access-dirty.log` chaos handling (3 valid out, 8 dead-letter, exact reconciliation), empty file handling |
| `PipelineLoaderTest` (17) | YAML loading, error policies, validate stage config, echo sinks, syntax error checking |
| `ParserStageTest` (14) | parsing CLF/Combined format, throws `StageException` on corrupted inputs |
| `PredicateFilterStageTest` (7) | filter predicates |
| `ProjectionStageTest` (5) | projection to requested fields |
| `FileSinkTest` (5), `ConsoleSinkTest` (1), `TeeSinkTest` (1) | I/O sinks and lifecycle management |
| `PipelineTest` (3), `PipelineBuilderIntegrationTest` (2) | pipeline execution, error wrapping, builder fluency |
| `LogRecordTest` (3), `FieldConditionTest` (3) | model and condition tests |

See [ARCHITECTURE.md](ARCHITECTURE.md) for the design and [CHANGELOG.md](CHANGELOG.md) for what changed.
