package logflow.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import logflow.core.Sink;

class TeeSinkTest {

    /** A sink that records what happens to it. */
    private static final class Recording implements Sink<String> {
        final List<String> events = new ArrayList<>();
        @Override public void open() { events.add("open"); }
        @Override public void consume(String item) { events.add(item); }
        @Override public void close() { events.add("close"); }
    }

    @Test
    void bothSinksGetEverything() {
        Recording a = new Recording();
        Recording b = new Recording();
        TeeSink<String> tee = new TeeSink<>(a, b);

        tee.open();
        tee.consume("x");
        tee.close();

        assertEquals(Arrays.asList("open", "x", "close"), a.events);
        assertEquals(Arrays.asList("open", "x", "close"), b.events);
    }
}
