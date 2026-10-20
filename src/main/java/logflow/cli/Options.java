package logflow.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Command-line arguments, parsed. Only reads values — building the pipeline is {@code Main}'s job.
 * <pre>
 * logflow &lt;input&gt; [--out FILE] [--status N] [--min-status N] [--method M] [--path-prefix P]
 *                  [--from ISO-INSTANT] [--to ISO-INSTANT] [--fields a,b,c]
 * </pre>
 */
public final class Options {

    public static final String USAGE =
            "Usage: logflow <log-file> [--out FILE] [--status 404] [--min-status 400] [--method GET]\n"
            + "                         [--path-prefix /api]\n"
            + "                         [--from 2026-09-28T05:00:00Z] [--to 2026-09-28T06:00:00Z]\n"
            + "                         [--fields timestamp,clientIp,status,path]";

    public static final String DEFAULT_FIELDS = "timestamp,clientIp,method,path,status,bytes";

    public Path input;
    /** Output file; {@code null} means print to the terminal. */
    public Path out;
    public Integer status;
    public Integer minStatus;
    public String method;
    public String pathPrefix;
    public Instant from;
    public Instant to;
    public String[] fields = DEFAULT_FIELDS.split(",");

    private Options() {}

    /** @throws IllegalArgumentException with a readable message if the arguments are wrong */
    public static Options parse(String[] args) {
        Options o = new Options();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                if (o.input != null) {
                    throw new IllegalArgumentException("Only one input file is allowed: " + arg);
                }
                o.input = Paths.get(arg);
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + arg);
            }
            String value = args[++i];
            switch (arg) {
                case "--out":         o.out = Paths.get(value); break;
                case "--min-status":  o.minStatus = parseInt(arg, value); break;
                case "--status":      o.status = parseInt(arg, value); break;
                case "--method":      o.method = value; break;
                case "--path-prefix": o.pathPrefix = value; break;
                case "--from":        o.from = parseInstant(arg, value); break;
                case "--to":          o.to = parseInstant(arg, value); break;
                case "--fields":      o.fields = value.split(","); break;
                default: throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        if (o.input == null) {
            throw new IllegalArgumentException("Missing input log file");
        }
        return o;
    }

    private static int parseInt(String option, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " expects a number, got: " + value);
        }
    }

    private static Instant parseInstant(String option, String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(option + " expects a time like 2026-09-28T05:00:00Z, got: " + value);
        }
    }
}
