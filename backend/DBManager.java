import java.sql.*;
import java.util.*;

/**
 * DBManager - Data Access Layer (JDBC)
 * 
 * Handles all database operations using JDBC with MySQL.
 * Implements full CRUD operations for the trades table.
 * All methods throw exceptions on failure so the Portfolio layer
 * can implement the file fallback strategy.
 * 
 * Database: forex
 * Table: trades (id, type, instrument, quantity, price, timestamp)
 */
public class DBManager {

    // ─── Database Configuration ──────────────────────────────────────────
    //  IMPORTANT: Update these credentials for your local MySQL setup
    private static final String DB_URL  = "jdbc:mysql://localhost:3306/forex";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Swayam9608@";  // ← Change to your MySQL password

    // JDBC Driver class name
    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    // ─── Connection Helper ───────────────────────────────────────────────

    /**
     * Establishes and returns a new database connection.
     * Throws Exception if connection fails (triggers file fallback).
     */
    private static Connection getConnection() throws Exception {
        Class.forName(DRIVER_CLASS);
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    // ─── Schema Initialization ───────────────────────────────────────────

    /**
     * Creates the forex database and trades table if they don't exist.
     * Called once during server startup.
     */
    public static void initializeSchema() throws Exception {
        // First connect without database to create it
        Class.forName(DRIVER_CLASS);
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/", DB_USER, DB_PASS)) {
            
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS forex");
            System.out.println("[DBManager] Database 'forex' verified/created.");
        }

        // Now connect to forex database and create table
        try (Connection conn = getConnection()) {
            String createTable = 
                "CREATE TABLE IF NOT EXISTS trades (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  type VARCHAR(10) NOT NULL," +
                "  instrument VARCHAR(50) NOT NULL," +
                "  quantity DOUBLE NOT NULL," +
                "  price DOUBLE NOT NULL," +
                "  timestamp VARCHAR(30) DEFAULT (NOW())" +
                ")";
            conn.createStatement().executeUpdate(createTable);
            System.out.println("[DBManager] Table 'trades' verified/created.");
        }
    }

    // ─── CREATE ──────────────────────────────────────────────────────────

    /**
     * Inserts a new trade into the database.
     * Returns the auto-generated ID of the new record.
     */
    public static int insert(Trade trade) throws Exception {
        String sql = "INSERT INTO trades (type, instrument, quantity, price, timestamp) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, trade.getType());
            ps.setString(2, trade.getInstrument());
            ps.setDouble(3, trade.getQuantity());
            ps.setDouble(4, trade.getPrice());
            ps.setString(5, trade.getTimestamp());

            int affectedRows = ps.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Insert failed, no rows affected.");
            }

            // Retrieve the auto-generated key
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newId = generatedKeys.getInt(1);
                    System.out.println("[DBManager] Inserted trade with ID: " + newId);
                    return newId;
                } else {
                    throw new SQLException("Insert succeeded but no ID obtained.");
                }
            }
        }
    }

    // ─── READ ────────────────────────────────────────────────────────────

    /**
     * Loads all trades from the database, ordered by ID.
     * Returns an empty list if no trades exist.
     */
    public static List<Trade> loadAll() throws Exception {
        List<Trade> trades = new ArrayList<>();
        String sql = "SELECT id, type, instrument, quantity, price, timestamp FROM trades ORDER BY id";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Trade trade = new Trade(
                    rs.getInt("id"),
                    rs.getString("type"),
                    rs.getString("instrument"),
                    rs.getDouble("quantity"),
                    rs.getDouble("price"),
                    rs.getString("timestamp")
                );
                trades.add(trade);
            }

            System.out.println("[DBManager] Loaded " + trades.size() + " trades from database.");
        }

        return trades;
    }

    /**
     * Loads a single trade by its ID.
     * Returns null if the trade is not found.
     */
    public static Trade loadById(int id) throws Exception {
        String sql = "SELECT id, type, instrument, quantity, price, timestamp FROM trades WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Trade(
                        rs.getInt("id"),
                        rs.getString("type"),
                        rs.getString("instrument"),
                        rs.getDouble("quantity"),
                        rs.getDouble("price"),
                        rs.getString("timestamp")
                    );
                }
            }
        }
        return null;
    }

    // ─── UPDATE ──────────────────────────────────────────────────────────

    /**
     * Updates the quantity and price of an existing trade.
     * Returns true if the update was successful.
     */
    public static boolean update(int id, double newQuantity, double newPrice) throws Exception {
        String sql = "UPDATE trades SET quantity = ?, price = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDouble(1, newQuantity);
            ps.setDouble(2, newPrice);
            ps.setInt(3, id);

            int affectedRows = ps.executeUpdate();
            System.out.println("[DBManager] Updated trade ID " + id + " (rows affected: " + affectedRows + ")");
            return affectedRows > 0;
        }
    }

    // ─── DELETE ──────────────────────────────────────────────────────────

    /**
     * Deletes a trade by its ID.
     * Returns true if the deletion was successful.
     */
    public static boolean delete(int id) throws Exception {
        String sql = "DELETE FROM trades WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            int affectedRows = ps.executeUpdate();
            System.out.println("[DBManager] Deleted trade ID " + id + " (rows affected: " + affectedRows + ")");
            return affectedRows > 0;
        }
    }

    // ─── Utility ─────────────────────────────────────────────────────────

    /**
     * Tests the database connection.
     * Returns true if connection is successful.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            System.out.println("[DBManager] Connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the count of trades in the database.
     */
    public static int getTradeCount() throws Exception {
        String sql = "SELECT COUNT(*) as count FROM trades";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt("count");
            }
        }
        return 0;
    }
}
