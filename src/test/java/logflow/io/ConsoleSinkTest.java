package logflow.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

class ConsoleSinkTest {

    @Test
    void printsOneLinePerItemAndCounts() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ConsoleSink<String> sink = new ConsoleSink<>(new PrintStream(buffer, true));

        sink.open();
        sink.consume("a");
        sink.consume("b");
        sink.close();

        assertEquals("a\nb\n", buffer.toString("UTF-8").replace(System.lineSeparator(), "\n"));
        assertEquals(2, sink.writtenCount());
    }
}
