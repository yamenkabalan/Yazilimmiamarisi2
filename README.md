# LogFlow — Increment 4: Configuration-Driven Composition and a Stage Registry

Software Architecture term project.

| | |
|---|---|
| **Student** | Mohamad Yamen Kabalan |
| **Student No** | 2404040356 |
| **Version** | `v4` |

## What it does

The whole pipeline — source, stages, sink — is described in a **YAML file**. The same,
unchanged program produces completely different results just by reading a different file:

```bash
logflow --config configs/error-report.yaml
```

A new pipeline needs a new YAML file, **not a recompile**.

## The three configurations

| Config | What it produces | Output |
|---|---|---|
| `configs/error-report.yaml` | every failed request (`status >= 400`): timestamp, clientIp, status, path | file `out/errors.txt` |
| `configs/api-traffic.yaml` | only `/api` requests: timestamp, method, path, status | terminal |
| `configs/bandwidth-by-client.yaml` | responses with a body (`bytes > 0`): clientIp, bytes | file `out/bandwidth.txt` |

```bash
logflow --config configs/error-report.yaml
logflow --config configs/api-traffic.yaml
logflow --config configs/bandwidth-by-client.yaml
```

```
Pipeline 'error-report': 57 records -> out/errors.txt (malformed lines skipped: 0)
Pipeline 'api-traffic': 40 records -> console (malformed lines skipped: 0)
Pipeline 'bandwidth-by-client': 186 records -> out/bandwidth.txt (malformed lines skipped: 0)
```

`configs/error-report.yaml`:

```yaml
pipeline:
  name: error-report
  source:
    type: file
    params: { path: data/access-small.log }
  stages:
    - type: parse
      params: { format: clf }
    - type: filter
      params: { field: status, op: ">=", value: 400 }
    - type: project
      params: { fields: [timestamp, clientIp, status, path] }
  sink:
    type: file
    params: { path: out/errors.txt }
```

The full schema (all types, parameters, fields and operators) is in
[ARCHITECTURE.md → Configuration schema](ARCHITECTURE.md#configuration-schema-v4).

## Error message examples

A mistake in the config is found **while loading, before any data is read**, and the
program stops with exit code 2 and a message that says where and what:

| Mistake in the YAML | Message |
|---|---|
| `- type: filtr` | `Config error in x.yaml: stage 2 (filtr): unknown stage type 'filtr'. Known types: [parse, filter, project]` |
| filter without `op` | `Config error in x.yaml: stage 2 (filter): missing required parameter 'op'` |
| `value: four-hundred` with `op: ">="` | `Config error in x.yaml: stage 2 (filter): op '>=' needs a number as value, got 'four-hundred'` |
| `fields: timestamp` (not a list) | `Config error in x.yaml: stage 2 (project): parameter 'fields' must be a non-empty list like [a, b], got: timestamp` |
| `stages: []` | `Config error in x.yaml: 'stages' is empty — a pipeline needs at least one stage, e.g. '- type: parse'` |
| `filter` before `parse` | `Config error in x.yaml: stage 1 (filter): needs LogRecord input but gets String. Check the order of the stages (e.g. 'parse' must come first).` |
| `feilds: [...]` (typo) | `Config error in x.yaml: stage 2 (project): unknown parameter 'feilds' (allowed: [fields])` |
| `fields: [ip]` | `Config error in x.yaml: stage 2 (project): unknown field 'ip'. Known fields: [timestamp, clientIp, method, path, status, bytes, userAgent, protocol, query, referer]` |
| `path: no/such.log` | `Config error in x.yaml: source (file): input file not found: no/such.log` |
| `sink: { type: printer }` | `Config error in x.yaml: sink: unknown type 'printer'. Known types: [file, console]` |

Malformed **log lines** are a different kind of error (run-time data errors): they are only
skipped and counted for now; week 5 handles them properly.

## Requirements, build, tests

- JDK 8 or newer to build (a JRE is enough to run the jar); Maven via the wrapper `mvnw.cmd`.
- `mvnw.cmd clean package` — compiles, runs **54 tests**, writes the coverage report to
  `target/site/jacoco/index.html`, and builds `target/logflow.jar` (SnakeYAML is packed inside it).

| Test class | What it checks |
|---|---|
| `PipelineLoaderTest` (12) | valid config, **unknown stage type, missing parameter, wrong-type parameter, empty pipeline**, wrong order, typo in a parameter, unknown field, missing input file, unknown sink, invalid YAML, and that the 3 shipped configs load |
| `FieldConditionTest` (3) | `field op value` conditions and rejected combinations |
| stage, sink, pipeline tests (39) | the earlier increments |

## Project structure

```
configs/                       error-report.yaml, api-traffic.yaml, bandwidth-by-client.yaml
src/main/java/logflow/
├── Main.java                  logflow --config <file.yaml>
├── config/
│   ├── StageRegistry.java     stage name → factory (the registration table)
│   ├── StageFactory.java      creates one stage from its params
│   ├── PipelineLoader.java    YAML → checked Pipeline (fails fast on any mistake)
│   ├── Params.java            typed, checked access to a stage's params
│   ├── LoadedPipeline.java    the built pipeline + what to report
│   └── ConfigException.java   a configuration-time error
├── core/                      Record, Stage, Emitter, Source, Sink, StageException
├── model/LogRecord.java       the parsed log entry
├── io/                        FileLineSource, FileSink, ConsoleSink
├── stage/                     ParserStage, PredicateFilterStage, FieldCondition, ProjectionStage
└── pipeline/                  Pipeline, PipelineBuilder, PipelineException
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for the design and [CHANGELOG.md](CHANGELOG.md) for what changed.
