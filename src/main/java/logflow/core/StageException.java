package logflow.core;

/**
 * Thrown by a {@link Stage} when an individual record cannot be processed.
 * <p>
 * This represents a <b>recoverable, record-level failure</b> (e.g. malformed syntax,
 * validation constraint failure, unexpected field value). The pipeline intercepts
 * this exception and handles it according to the stage's configured {@link ErrorPolicy}
 * (e.g. routing to dead-letter, skipping, or failing fast).
 */
public class StageException extends Exception {

    private static final long serialVersionUID = 1L;

    public StageException(String message) {
        super(message);
    }

    public StageException(String message, Throwable cause) {
        super(message, cause);
    }
}
