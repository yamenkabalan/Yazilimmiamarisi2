package logflow.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import logflow.testing.CollectingEmitter;

class LimitStageTest {

    @Test
    void passesOnlyTheFirstNItems() {
        LimitStage<String> limit = new LimitStage<>(2);
        limit.open();
        CollectingEmitter<String> out = new CollectingEmitter<>();

        for (String s : Arrays.asList("a", "b", "c", "d")) {
            limit.process(s, out);
        }

        assertEquals(Arrays.asList("a", "b"), out.items());
    }

    @Test
    void openResetsTheCounter() {
        LimitStage<String> limit = new LimitStage<>(1);
        CollectingEmitter<String> out = new CollectingEmitter<>();
        limit.open();
        limit.process("a", out);
        limit.open();
        limit.process("b", out);

        assertEquals(Arrays.asList("a", "b"), out.items());
    }

    @Test
    void negativeLimitIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LimitStage<String>(-1));
    }
}
