package logflow.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import logflow.core.Emitter;
import logflow.core.Source;

/** Reads a text file and emits one {@code String} per line. */
public final class FileLineSource implements Source<String> {

    private final Path file;

    public FileLineSource(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    @Override
    public void produce(Emitter<String> out) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.emit(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read file: " + file, e);
        }
    }
}
