package logflow.pipeline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable execution report produced at the end of a pipeline run.
 * <p>
 * Tracks:
 * <ul>
 *   <li>Records in: count of records emitted by the source.</li>
 *   <li>Records out: count of records successfully delivered to the primary sink.</li>
 *   <li>Dead-letter records: count of failed records routed to the secondary dead-letter sink.</li>
 *   <li>Dropped records per stage: count of records dropped (filtered or skipped) at each stage.</li>
 *   <li>Execution duration: wall-clock time elapsed during the run in milliseconds.</li>
 * </ul>
 * Also verifies the conservation equation: {@code recordsIn == recordsOut + deadLetterCount + totalDropped}.
 */
public final class RunSummary {

    private final String pipelineName;
    private final long recordsIn;
    private final long recordsOut;
    private final long deadLetterCount;
    private final Map<String, Long> droppedPerStage;
    private final Map<String, Long> deadLetterPerStage;
    private final Map<String, Long> errorsPerStage;
    private final long durationMillis;

    public RunSummary(String pipelineName,
                      long recordsIn,
                      long recordsOut,
                      long deadLetterCount,
                      Map<String, Long> droppedPerStage,
                      Map<String, Long> deadLetterPerStage,
                      Map<String, Long> errorsPerStage,
                      long durationMillis) {
        this.pipelineName = pipelineName != null ? pipelineName : "pipeline";
        this.recordsIn = recordsIn;
        this.recordsOut = recordsOut;
        this.deadLetterCount = deadLetterCount;
        this.droppedPerStage = Collections.unmodifiableMap(new LinkedHashMap<>(droppedPerStage));
        this.deadLetterPerStage = Collections.unmodifiableMap(new LinkedHashMap<>(deadLetterPerStage));
        this.errorsPerStage = Collections.unmodifiableMap(new LinkedHashMap<>(errorsPerStage));
        this.durationMillis = durationMillis;
    }

    public String pipelineName() {
        return pipelineName;
    }

    /** Number of records produced by the source into the pipeline. */
    public long recordsIn() {
        return recordsIn;
    }

    /** Number of records that reached the primary sink. */
    public long recordsOut() {
        return recordsOut;
    }

    /** Number of failed records routed to the dead-letter channel. */
    public long deadLetterCount() {
        return deadLetterCount;
    }

    /** Wall-clock execution time in milliseconds. */
    public long durationMillis() {
        return durationMillis;
    }

    /** Number of dropped / skipped records per stage name. */
    public Map<String, Long> droppedPerStage() {
        return droppedPerStage;
    }

    /** Number of dead-lettered records per stage name. */
    public Map<String, Long> deadLetterPerStage() {
        return deadLetterPerStage;
    }

    /** Total count of errors encountered per stage name. */
    public Map<String, Long> errorsPerStage() {
        return errorsPerStage;
    }

    /** Sum of all records dropped across all stages. */
    public long totalDropped() {
        long sum = 0;
        for (long count : droppedPerStage.values()) {
            sum += count;
        }
        return sum;
    }

    /**
     * Checks whether all input records are completely accounted for:
     * {@code recordsIn == recordsOut + deadLetterCount + totalDropped}.
     */
    public boolean isAccountedFor() {
        return recordsIn == (recordsOut + deadLetterCount + totalDropped());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("============================================================\n");
        sb.append("Pipeline Run Summary: ").append(pipelineName).append("\n");
        sb.append("============================================================\n");
        sb.append(String.format("Duration:               %d ms\n", durationMillis));
        sb.append(String.format("Records in:             %d\n", recordsIn));
        sb.append(String.format("Records out:            %d\n", recordsOut));
        sb.append(String.format("Dead-letter records:    %d\n", deadLetterCount));
        sb.append("Dropped per stage:\n");
        if (droppedPerStage.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (Map.Entry<String, Long> e : droppedPerStage.entrySet()) {
                sb.append(String.format("  - %-18s %d\n", e.getKey() + ":", e.getValue()));
            }
        }
        sb.append(String.format("Total dropped:          %d\n", totalDropped()));
        sb.append(String.format("Accounting check:       %s (%d in == %d out + %d dead-letter + %d dropped)\n",
                isAccountedFor() ? "EXACT MATCH" : "MISMATCH",
                recordsIn, recordsOut, deadLetterCount, totalDropped()));
        sb.append("============================================================");
        return sb.toString();
    }
}

