package logflow.stage;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import logflow.core.Emitter;
import logflow.core.Stage;
import logflow.core.StageException;
import logflow.model.LogRecord;

/**
 * Parses a Common Log Format (CLF) line into a {@link LogRecord}.
 * <p>
 * The Combined Log Format (CLF + {@code "referer" "user-agent"}) is accepted too, since the
 * sample data uses it. Leading/trailing whitespace and repeated spaces between fields are tolerated.
 * <p>
 * <b>Malformed lines:</b> in v5, the "silently skip" approach has been replaced with the pipeline's
 * designed error strategy. When a line cannot be parsed, this stage throws a {@link StageException}.
 * The pipeline's configured {@link logflow.core.ErrorPolicy} (e.g. DEAD_LETTER or SKIP) then routes or counts the record.
 */
public final class ParserStage implements Stage<String, LogRecord> {

    //  host ident user [time] "request" status bytes ["referer" "user-agent"]
    private static final Pattern LINE = Pattern.compile(
            "^(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+\\[([^\\]]+)\\]\\s+\"([^\"]*)\"\\s+(\\S+)\\s+(\\S+)"
                    + "(?:\\s+\"([^\"]*)\"\\s+\"([^\"]*)\")?$");

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

    private long malformed;

    @Override
    public void open() {
        malformed = 0;
    }

    @Override
    public void process(String line, Emitter<LogRecord> out) throws StageException {
        if (line == null || line.trim().isEmpty()) {
            malformed++;
            throw new StageException("Empty or blank log line");
        }
        if (line.length() > 65536) {
            malformed++;
            throw new StageException("Line length (" + line.length() + " chars) exceeds maximum allowed limit");
        }
        if (line.contains("\uFFFD")) {
            malformed++;
            throw new StageException("Invalid UTF-8 byte sequences detected in log line");
        }

        LogRecord record = parse(line);
        if (record == null) {
            malformed++;
            throw new StageException("Malformed CLF log line: "
                    + (line.length() > 120 ? line.substring(0, 120) + "..." : line));
        }
        out.emit(record);
    }

    /** Number of lines that failed to parse since the last {@link #open()}. */
    public long malformedCount() {
        return malformed;
    }

    /** Returns the parsed record, or {@code null} if the line is malformed. */
    private static LogRecord parse(String line) {
        if (line == null) {
            return null;
        }
        Matcher m = LINE.matcher(line.trim());
        if (!m.matches()) {
            return null;
        }

        Instant timestamp;
        try {
            timestamp = OffsetDateTime.parse(m.group(4), TIME).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }

        String[] request = m.group(5).trim().split("\\s+");
        if (request.length != 3) {
            return null;
        }

        int status;
        try {
            status = Integer.parseInt(m.group(6));
        } catch (NumberFormatException e) {
            return null;
        }
        if (status < 100 || status > 599) {
            return null;
        }

        long bytes;
        if ("-".equals(m.group(7))) {
            bytes = 0;
        } else {
            try {
                bytes = Long.parseLong(m.group(7));
            } catch (NumberFormatException e) {
                return null;
            }
            if (bytes < 0) {
                return null;
            }
        }

        String target = request[1];
        int q = target.indexOf('?');
        String path = q < 0 ? target : target.substring(0, q);

        LogRecord.Builder b = LogRecord.builder()
                .timestamp(timestamp)
                .clientIp(m.group(1))
                .method(request[0])
                .path(path)
                .status(status)
                .bytes(bytes)
                .userAgent(m.group(9) != null ? m.group(9) : "-")
                .raw(line)
                .attribute("protocol", request[2]);
        if (q >= 0) {
            b.attribute("query", target.substring(q + 1));
        }
        if (!"-".equals(m.group(2))) {
            b.attribute("ident", m.group(2));
        }
        if (!"-".equals(m.group(3))) {
            b.attribute("user", m.group(3));
        }
        if (m.group(8) != null && !"-".equals(m.group(8))) {
            b.attribute("referer", m.group(8));
        }
        return b.build();
    }
}
