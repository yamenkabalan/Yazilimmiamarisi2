package logflow.core;

/**
 * The unit of data that flows through the pipeline.
 * <p>
 * In v1 the pipeline carries raw {@code String} lines, so this interface only
 * names the concept. Later increments will parse lines into structured records.
 */
public interface Record {

    /** The original text this record was built from. */
    String raw();
}
