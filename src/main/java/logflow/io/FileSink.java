package logflow.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import logflow.core.Sink;

/**
 * Writes each item as one line to a file. The writer is buffered: lines sit in memory until
 * the buffer is flushed. {@link #close()} flushes and closes it — skip it and output is silently lost.
 */
public final class FileSink<T> implements Sink<T> {

    private final Path file;
    private BufferedWriter writer;
    private long written;

    public FileSink(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath();
    }

    @Override
    public void open() {
        try {
            Files.createDirectories(file.getParent());
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            written = 0;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open " + file, e);
        }
    }

    @Override
    public void consume(T item) {
        if (writer == null) throw new IllegalStateException("FileSink is not open");
        try {
            writer.write(String.valueOf(item));
            writer.newLine();
            written++;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write to " + file, e);
        }
    }

    @Override
    public void close() {
        if (writer == null) return;
        try {
            writer.close();   // flushes the buffer first
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot close " + file, e);
        } finally {
            writer = null;
        }
    }

    /** Number of lines written since the last {@link #open()}. */
    public long writtenCount() {
        return written;
    }
}
