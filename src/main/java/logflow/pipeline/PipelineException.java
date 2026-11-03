package logflow.pipeline;

/**
 * Thrown when the pipeline as a whole cannot proceed.
 * <p>
 * This represents an <b>unrecoverable, pipeline-level failure</b> (e.g. invalid configuration,
 * unreadable source file, disk full, fatal I/O failure, or when a stage error policy is FAIL_FAST).
 * Halts execution of the pipeline immediately.
 */
public class PipelineException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PipelineException(String message) {
        super(message);
    }

    public PipelineException(String message, Throwable cause) {
        super(message, cause);
    }
}
