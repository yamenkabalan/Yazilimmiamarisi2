package logflow.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
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
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file), decoder))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.emit(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read file: " + file, e);
        }
    }
}
