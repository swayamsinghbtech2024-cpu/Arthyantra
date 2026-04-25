import java.util.*;
import java.util.stream.Collectors;

/**
 * Portfolio Class - Business Logic Layer
 * 
 * Manages the collection of trades and provides portfolio analysis methods.
 * Acts as the central service layer between the controller and data access layers.
 * Implements DB-first with file fallback storage strategy.
 */
public class Portfolio {

    // ─── Fields ──────────────────────────────────────────────────────────
    private List<Trade> trades;           // In-memory list of all trades
    private boolean dbAvailable;          // Tracks if database is currently accessible

    // ─── Constructor ─────────────────────────────────────────────────────

    public Portfolio() {
        this.trades = new ArrayList<>();
        this.dbAvailable = true;
    }

    // ─── Trade Management ────────────────────────────────────────────────

    /**
     * Adds a new trade to the portfolio.
     * Persists to DB first; if that fails, uses file storage as fallback.
     * Returns the trade with assigned ID.
     */
    public Trade addTrade(Trade trade) {
        // Attempt DB insert first
        try {
            int newId = DBManager.insert(trade);
            if (newId > 0) {
                trade.setId(newId);
                dbAvailable = true;
                System.out.println("[Portfolio] Trade saved to database (ID: " + newId + ")");
            } else {
                throw new Exception("DB insert returned no ID");
            }
        } catch (Exception e) {
            dbAvailable = false;
            System.out.println("[Portfolio] DB insert failed, using file fallback: " + e.getMessage());

            // Generate file-based ID (max existing ID + 1)
            int maxId = trades.stream().mapToInt(Trade::getId).max().orElse(0);
            trade.setId(maxId + 1);
        }

        // Add to in-memory list
        trades.add(trade);

        // Save to file as backup (always, regardless of DB status)
        saveToFile();

        return trade;
    }

    /**
     * Retrieves all trades in the portfolio.
     */
    public List<Trade> getTrades() {
        return Collections.unmodifiableList(trades);
    }

    /**
     * Finds a trade by its ID.
     * Returns null if not found.
     */
    public Trade getTradeById(int id) {
        return trades.stream()
                .filter(t -> t.getId() == id)
                .findFirst()
                .orElse(null);
    }

    /**
     * Updates an existing trade's quantity and price.
     * Persists changes to DB and file storage.
     */
    public boolean updateTrade(int id, double newQuantity, double newPrice) {
        Trade target = getTradeById(id);
        if (target == null) {
            System.out.println("[Portfolio] Trade not found for update: ID " + id);
            return false;
        }

        // Update in-memory values
        target.setQuantity(newQuantity);
        target.setPrice(newPrice);

        // Attempt DB update
        try {
            DBManager.update(id, newQuantity, newPrice);
            dbAvailable = true;
            System.out.println("[Portfolio] Trade updated in database (ID: " + id + ")");
        } catch (Exception e) {
            dbAvailable = false;
            System.out.println("[Portfolio] DB update failed, file-only: " + e.getMessage());
        }

        // Save to file backup
        saveToFile();
        return true;
    }

    /**
     * Deletes a trade by its ID.
     * Removes from DB, in-memory list, and file storage.
     */
    public boolean deleteTrade(int id) {
        Trade target = getTradeById(id);
        if (target == null) {
            System.out.println("[Portfolio] Trade not found for deletion: ID " + id);
            return false;
        }

        // Remove from in-memory list
        trades.removeIf(t -> t.getId() == id);

        // Attempt DB delete
        try {
            DBManager.delete(id);
            dbAvailable = true;
            System.out.println("[Portfolio] Trade deleted from database (ID: " + id + ")");
        } catch (Exception e) {
            dbAvailable = false;
            System.out.println("[Portfolio] DB delete failed, file-only: " + e.getMessage());
        }

        // Save updated list to file
        saveToFile();
        return true;
    }

    // ─── Portfolio Analytics ─────────────────────────────────────────────

    /**
     * Calculates the total profit/loss across all trades.
     * BUY trades reduce profit (money spent), SELL trades increase profit (money earned).
     */
    public double calculateProfitLoss() {
        double totalPL = 0.0;
        for (Trade trade : trades) {
            double tradeValue = trade.getQuantity() * trade.getPrice();
            if ("BUY".equals(trade.getType())) {
                totalPL -= tradeValue; // Buying costs money
            } else {
                totalPL += tradeValue; // Selling earns money
            }
        }
        return totalPL;
    }

    /**
     * Gets the total number of trades in the portfolio.
     */
    public int getTradeCount() {
        return trades.size();
    }

    /**
     * Gets a summary of trades grouped by instrument.
     */
    public Map<String, List<Trade>> getTradesByInstrument() {
        return trades.stream().collect(Collectors.groupingBy(Trade::getInstrument));
    }

    /**
     * Calculates total invested value (sum of all BUY trade values).
     */
    public double getTotalInvested() {
        return trades.stream()
                .filter(t -> "BUY".equals(t.getType()))
                .mapToDouble(t -> t.getQuantity() * t.getPrice())
                .sum();
    }

    /**
     * Calculates total revenue (sum of all SELL trade values).
     */
    public double getTotalRevenue() {
        return trades.stream()
                .filter(t -> "SELL".equals(t.getType()))
                .mapToDouble(t -> t.getQuantity() * t.getPrice())
                .sum();
    }

    // ─── Data Loading ────────────────────────────────────────────────────

    /**
     * Loads trades from the database. Falls back to file if DB fails.
     * This is called during server startup.
     */
    public void loadTrades() {
        // Try loading from database first
        try {
            List<Trade> dbTrades = DBManager.loadAll();
            if (dbTrades != null && !dbTrades.isEmpty()) {
                trades = new ArrayList<>(dbTrades);
                dbAvailable = true;
                System.out.println("[Portfolio] Loaded " + trades.size() + " trades from database.");
                return;
            }
            System.out.println("[Portfolio] Database is empty, checking file backup...");
            dbAvailable = true;
        } catch (Exception e) {
            dbAvailable = false;
            System.out.println("[Portfolio] Database unavailable: " + e.getMessage());
        }

        // Fallback: Load from file
        try {
            List<Trade> fileTrades = FileManager.load();
            if (fileTrades != null && !fileTrades.isEmpty()) {
                trades = new ArrayList<>(fileTrades);
                System.out.println("[Portfolio] Loaded " + trades.size() + " trades from file backup.");
            } else {
                System.out.println("[Portfolio] No existing trades found. Starting fresh.");
            }
        } catch (Exception e) {
            System.out.println("[Portfolio] File load also failed: " + e.getMessage());
            trades = new ArrayList<>();
        }
    }

    // ─── File Backup ─────────────────────────────────────────────────────

    /**
     * Saves all current trades to file as a backup.
     * Called after every write operation.
     */
    private void saveToFile() {
        try {
            FileManager.save(trades);
            System.out.println("[Portfolio] File backup saved (" + trades.size() + " trades).");
        } catch (Exception e) {
            System.out.println("[Portfolio] WARNING: File backup failed: " + e.getMessage());
        }
    }

    // ─── Status ──────────────────────────────────────────────────────────

    /**
     * Returns whether the database is currently accessible.
     */
    public boolean isDbAvailable() {
        return dbAvailable;
    }

    /**
     * Converts the portfolio summary to JSON format.
     */
    public String toSummaryJSON() {
        return "{" +
                "\"tradeCount\":" + getTradeCount() + "," +
                "\"profitLoss\":" + String.format("%.2f", calculateProfitLoss()) + "," +
                "\"totalInvested\":" + String.format("%.2f", getTotalInvested()) + "," +
                "\"totalRevenue\":" + String.format("%.2f", getTotalRevenue()) + "," +
                "\"dbStatus\":\"" + (dbAvailable ? "connected" : "disconnected") + "\"" +
                "}";
    }
}