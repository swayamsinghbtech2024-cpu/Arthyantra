import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Trade Model Class
 * Represents a single forex/commodity/index trade with all relevant attributes.
 * Supports serialization to JSON and CSV formats for API responses and file storage.
 */
public class Trade {

    // ─── Fields ──────────────────────────────────────────────────────────
    private int id;                   // Unique identifier (DB auto-increment or file index)
    private String type;              // Trade type: "BUY" or "SELL"
    private String instrument;        // Instrument name (e.g., "USD/INR", "GOLD", "NIFTY 50")
    private double quantity;          // Number of units traded
    private double price;             // Price per unit at time of trade
    private String timestamp;         // ISO timestamp of when the trade was created

    // Date formatter for consistent timestamp formatting
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ─── Constructors ───────────────────────────────────────────────────

    /**
     * Full constructor with all fields (used when loading from DB).
     */
    public Trade(int id, String type, String instrument, double quantity, double price, String timestamp) {
        this.id = id;
        this.type = type;
        this.instrument = instrument;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = timestamp;
    }

    /**
     * Constructor without ID and timestamp (used when creating new trades).
     * Automatically generates a timestamp.
     */
    public Trade(String type, String instrument, double quantity, double price) {
        this.id = 0; // Will be assigned by DB or FileManager
        this.type = type;
        this.instrument = instrument;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = LocalDateTime.now().format(FORMATTER);
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getInstrument() { return instrument; }
    public void setInstrument(String instrument) { this.instrument = instrument; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    // ─── Serialization Methods ───────────────────────────────────────────

    /**
     * Converts this Trade to a JSON string representation.
     * Manual JSON building to avoid external dependencies.
     */
    public String toJSON() {
        return "{" +
                "\"id\":" + id + "," +
                "\"type\":\"" + escapeJSON(type) + "\"," +
                "\"instrument\":\"" + escapeJSON(instrument) + "\"," +
                "\"quantity\":" + quantity + "," +
                "\"price\":" + price + "," +
                "\"timestamp\":\"" + escapeJSON(timestamp) + "\"" +
                "}";
    }

    /**
     * Converts this Trade to a CSV line for file storage.
     * Format: id,type,instrument,quantity,price,timestamp
     */
    public String toCSV() {
        return id + "," + type + "," + instrument + "," + quantity + "," + price + "," + timestamp;
    }

    /**
     * Creates a Trade object from a CSV line.
     * Handles parsing errors gracefully.
     */
    public static Trade fromCSV(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        String[] parts = line.split(",", 6); // Limit split to 6 parts
        if (parts.length < 6) {
            System.out.println("[Trade] Warning: Invalid CSV line: " + line);
            return null;
        }
        try {
            int id = Integer.parseInt(parts[0].trim());
            String type = parts[1].trim();
            String instrument = parts[2].trim();
            double quantity = Double.parseDouble(parts[3].trim());
            double price = Double.parseDouble(parts[4].trim());
            String timestamp = parts[5].trim();
            return new Trade(id, type, instrument, quantity, price, timestamp);
        } catch (NumberFormatException e) {
            System.out.println("[Trade] Error parsing CSV line: " + e.getMessage());
            return null;
        }
    }

    // ─── Validation ─────────────────────────────────────────────────────

    /**
     * Validates trade data before processing.
     * Returns an error message if invalid, or null if valid.
     */
    public static String validate(String type, String instrument, double quantity, double price) {
        if (type == null || (!type.equals("BUY") && !type.equals("SELL"))) {
            return "Invalid trade type. Must be BUY or SELL.";
        }
        if (instrument == null || instrument.trim().isEmpty()) {
            return "Instrument cannot be empty.";
        }
        if (quantity <= 0) {
            return "Quantity must be greater than zero.";
        }
        if (price <= 0) {
            return "Price must be greater than zero.";
        }
        return null; // Valid
    }

    // ─── Utility ─────────────────────────────────────────────────────────

    /**
     * Escapes special characters in strings for JSON output.
     */
    private static String escapeJSON(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }

    @Override
    public String toString() {
        return String.format("[%d] %s %s | Qty: %.2f | Price: %.2f | %s",
                id, type, instrument, quantity, price, timestamp);
    }
}