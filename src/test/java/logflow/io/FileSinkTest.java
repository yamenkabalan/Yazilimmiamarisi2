package logflow.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** FileSink's job is writing files, so this test (unlike the stage tests) uses a temp directory. */
class FileSinkTest {

    @TempDir
    Path dir;

    @Test
    void writesOneLinePerItemAfterClose() throws Exception {
        Path file = dir.resolve("out.txt");
        FileSink<String> sink = new FileSink<>(file);

        sink.open();
        sink.consume("a");
        sink.consume("b");
        sink.close();

        assertEquals(Arrays.asList("a", "b"), Files.readAllLines(file, StandardCharsets.UTF_8));
        assertEquals(2, sink.writtenCount());
    }

    @Test
    void withoutCloseTheBufferedOutputIsLost() throws Exception {
        Path file = dir.resolve("lost.txt");
        FileSink<String> sink = new FileSink<>(file);

        sink.open();
        sink.consume("this line is still in the buffer");
        // no close(): nothing has reached the disk yet
        assertEquals(0, Files.size(file));

        sink.close();
        assertTrue(Files.size(file) > 0);
    }

    @Test
    void createsMissingParentDirectories() throws Exception {
        Path file = dir.resolve("a/b/out.txt");
        FileSink<String> sink = new FileSink<>(file);
        sink.open();
        sink.close();
        assertTrue(Files.exists(file));
    }

    @Test
    void consumeBeforeOpenFails() {
        FileSink<String> sink = new FileSink<>(dir.resolve("x.txt"));
        assertThrows(IllegalStateException.class, () -> sink.consume("x"));
    }

    @Test
    void closeTwiceIsHarmless() {
        FileSink<String> sink = new FileSink<>(dir.resolve("y.txt"));
        sink.open();
        sink.close();
        sink.close();
    }
}
