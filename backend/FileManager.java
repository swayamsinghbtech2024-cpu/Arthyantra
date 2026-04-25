import java.io.*;
import java.util.*;

/**
 * FileManager - Data Access Layer (File I/O)
 * 
 * Provides file-based persistence as a fallback when the database is unavailable.
 * Stores trades in CSV format in the data/trades.txt file.
 * Implements thread-safe read/write operations.
 * 
 * File Format (CSV): id,type,instrument,quantity,price,timestamp
 */
public class FileManager {

    // ─── Configuration ───────────────────────────────────────────────────
    // Path relative to the backend directory where server runs
    private static final String DATA_DIR = "../data";
    private static final String TRADES_FILE = DATA_DIR + "/trades.txt";
    private static final String BACKUP_FILE = DATA_DIR + "/trades_backup.txt";

    // ─── Initialization ──────────────────────────────────────────────────

    /**
     * Ensures the data directory exists.
     * Called during server startup.
     */
    public static void initialize() {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            System.out.println("[FileManager] Data directory " + 
                (created ? "created" : "already exists") + ": " + dir.getAbsolutePath());
        }
    }

    // ─── SAVE (Write All Trades) ─────────────────────────────────────────

    /**
     * Saves all trades to the file, overwriting existing content.
     * Creates a backup of the previous file before writing.
     * Thread-safe via synchronized access.
     * 
     * @param trades List of Trade objects to persist
     * @throws Exception if file write fails
     */
    public static synchronized void save(List<Trade> trades) throws Exception {
        File tradesFile = new File(TRADES_FILE);

        // Create backup of existing file before overwriting
        if (tradesFile.exists()) {
            File backup = new File(BACKUP_FILE);
            copyFile(tradesFile, backup);
        }

        // Write all trades to file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(TRADES_FILE))) {
            for (Trade trade : trades) {
                writer.write(trade.toCSV());
                writer.newLine();
            }
            writer.flush();
            System.out.println("[FileManager] Saved " + trades.size() + " trades to file.");
        } catch (IOException e) {
            System.out.println("[FileManager] Error saving trades: " + e.getMessage());
            throw e;
        }
    }

    // ─── LOAD (Read All Trades) ──────────────────────────────────────────

    /**
     * Loads all trades from the file.
     * Returns an empty list if the file doesn't exist or is empty.
     * Skips invalid/corrupted lines gracefully.
     * 
     * @return List of Trade objects loaded from file
     * @throws Exception if file read fails critically
     */
    public static synchronized List<Trade> load() throws Exception {
        List<Trade> trades = new ArrayList<>();
        File file = new File(TRADES_FILE);

        // Return empty list if file doesn't exist yet
        if (!file.exists()) {
            System.out.println("[FileManager] No trades file found. Starting fresh.");
            return trades;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNum = 0;
            int skipped = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                // Parse the CSV line into a Trade object
                Trade trade = Trade.fromCSV(line);
                if (trade != null) {
                    trades.add(trade);
                } else {
                    skipped++;
                    System.out.println("[FileManager] Skipped invalid line " + lineNum + ": " + line);
                }
            }

            System.out.println("[FileManager] Loaded " + trades.size() + " trades from file." +
                    (skipped > 0 ? " Skipped " + skipped + " invalid lines." : ""));

        } catch (IOException e) {
            System.out.println("[FileManager] Error reading trades file: " + e.getMessage());
            throw e;
        }

        return trades;
    }

    // ─── APPEND (Add Single Trade) ───────────────────────────────────────

    /**
     * Appends a single trade to the end of the file.
     * More efficient than rewriting the entire file for single additions.
     * 
     * @param trade The trade to append
     * @throws Exception if file write fails
     */
    public static synchronized void append(Trade trade) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(TRADES_FILE, true))) {
            writer.write(trade.toCSV());
            writer.newLine();
            writer.flush();
            System.out.println("[FileManager] Appended trade ID " + trade.getId() + " to file.");
        } catch (IOException e) {
            System.out.println("[FileManager] Error appending trade: " + e.getMessage());
            throw e;
        }
    }

    // ─── File Utility ────────────────────────────────────────────────────

    /**
     * Copies a file from source to destination.
     * Used for creating backups before overwriting.
     */
    private static void copyFile(File source, File dest) {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        } catch (IOException e) {
            System.out.println("[FileManager] Backup copy failed: " + e.getMessage());
        }
    }

    /**
     * Checks if the trades file exists and is readable.
     */
    public static boolean fileExists() {
        File file = new File(TRADES_FILE);
        return file.exists() && file.canRead();
    }

    /**
     * Returns the absolute path of the trades file.
     */
    public static String getFilePath() {
        return new File(TRADES_FILE).getAbsolutePath();
    }

    /**
     * Clears the trades file (used for testing).
     */
    public static void clear() throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(TRADES_FILE))) {
            // Write nothing - effectively clears the file
            writer.flush();
        }
        System.out.println("[FileManager] Trades file cleared.");
    }
}