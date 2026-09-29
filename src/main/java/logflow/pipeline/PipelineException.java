package logflow.pipeline;

/** Raised by {@link Pipeline#run()} when a stage fails. */
public class PipelineException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PipelineException(String message, Throwable cause) {
        super(message, cause);
    }
}
