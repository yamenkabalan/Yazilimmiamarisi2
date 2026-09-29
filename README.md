# LogFlow — Increment 1: The Skeleton Pipeline

Software Architecture term project.

| | |
|---|---|
| **Student** | Mohamad Yamen Kabalan |
| **Student No** | 2404040356 |
| **Version** | `v1` |

## What it does

`logflow` reads a text (log) file and prints every line to the console —
**through a pipeline** (Source → Stages → Sink), not through a `main` method that does everything.

## Requirements

- Java 8 or newer (JDK)
- Maven 3.x (optional, see "Build without Maven")

## Build

```bash
mvn package
```

This produces `target/logflow.jar`.

### Build without Maven

```bash
mkdir -p out
javac -d out $(find src/main/java -name "*.java")
jar cfe target/logflow.jar logflow.Main -C out .
```

## Run

```bash
./logflow data/access-small.log        # Linux / macOS / Git Bash
logflow data\access-small.log          # Windows cmd
java -jar target/logflow.jar data/access-small.log
```

Expected result: all 200 lines of `data/access-small.log` are printed to the console.

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
├── io/                        implementations
│   ├── FileLineSource.java    emits one String per line of a file
│   └── ConsoleSink.java       prints each record
└── pipeline/
    ├── Pipeline.java          ordered list of stages, run() pushes source → sink
    └── PipelineException.java
data/access-small.log          200-line sample Apache access log
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for the design.
