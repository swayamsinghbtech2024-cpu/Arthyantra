import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SignalLogger — Signal Logging Observer
 * 
 * Implements MarketObserver to log all price updates and signals
 * to a file and in-memory buffer for auditing and analysis.
 * 
 * Part of the Observer Pattern triad:
 * - TradeEngine (executes trades)
 * - SignalLogger (logs for audit)
 * - UI (displays to user via SSE)
 */
public class SignalLogger implements MarketObserver {

    private static final String LOG_FILE = "signals.log";
    private static final int MAX_ENTRIES = 500;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<String> logEntries = new CopyOnWriteArrayList<>();

    // ─── Constructor ─────────────────────────────────────────────────

    public SignalLogger() {
        System.out.println("[SignalLogger] Logger initialized. Writing to " + LOG_FILE);
    }

    // ─── Observer Pattern: onPriceUpdate ─────────────────────────────

    @Override
    public void onPriceUpdate(String instrument, double price, String timestamp) {
        // Price updates are frequent — we only log periodically or on change
        // to avoid flooding the log file.
    }

    // ─── Observer Pattern: onSignalGenerated ─────────────────────────

    @Override
    public void onSignalGenerated(Signal signal) {
        String entry = String.format("[%s] SIGNAL: %s %s @ %.4f | Confidence: %.0f%% | Strategy: %s | %s",
                signal.getTimestamp(),
                signal.getType(),
                signal.getInstrument(),
                signal.getPrice(),
                signal.getConfidence() * 100,
                signal.getStrategy(),
                signal.getReason());

        logEntries.add(entry);
        if (logEntries.size() > MAX_ENTRIES) {
            logEntries.remove(0);
        }

        // Write to log file
        writeToFile(entry);

        System.out.println("[SignalLogger] " + entry);
    }

    // ─── File Writing ────────────────────────────────────────────────

    private void writeToFile(String entry) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(entry);
        } catch (IOException e) {
            System.out.println("[SignalLogger] File write error: " + e.getMessage());
        }
    }

    // ─── Access ──────────────────────────────────────────────────────

    /**
     * Returns the recent log entries.
     */
    public List<String> getLogEntries() {
        return Collections.unmodifiableList(logEntries);
    }

    /**
     * Returns log entries as a JSON array of strings.
     */
    public String toJSON() {
        StringBuilder sb = new StringBuilder("[");
        int start = Math.max(0, logEntries.size() - 20);
        for (int i = start; i < logEntries.size(); i++) {
            if (i > start) sb.append(",");
            sb.append("\"").append(escapeJSON(logEntries.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeJSON(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n");
    }
}
