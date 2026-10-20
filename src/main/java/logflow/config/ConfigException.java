package logflow.config;

/**
 * A mistake in the configuration file (unknown stage, missing or wrong parameter, …).
 * Thrown while loading — before any data is read — so the program fails fast and loudly.
 * (Bad data lines at run time are a different kind of error: see week 5.)
 */
public class ConfigException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConfigException(String message) {
        super(message);
    }
}
