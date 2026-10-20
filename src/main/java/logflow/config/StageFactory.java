package logflow.config;

import logflow.core.Stage;

/** Creates one stage from its config parameters. */
@FunctionalInterface
public interface StageFactory {
    Stage<?, ?> create(Params params);
}
