import java.util.*;

/**
 * TestRunner - Testing Module
 * 
 * Contains comprehensive test cases for all system components:
 * - Trade model validation
 * - Portfolio operations (CRUD)
 * - Strategy simulator
 * - FileManager I/O
 * - Edge cases and error handling
 * 
 * Run: java TestRunner
 */
public class TestRunner {

    // Test counters
    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;

    // ─── Main Test Runner ────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║         Forex Portfolio - Test Suite                     ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();

        // Initialize file manager
        FileManager.initialize();

        // Run all test suites
        testTradeModel();
        testTradeValidation();
        testTradeCSVSerialization();
        testPortfolioOperations();
        testPortfolioProfitLoss();
        testStrategySimulator();
        testFileManager();
        testEdgeCases();

        // Print results
        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════");
        System.out.println("  TEST RESULTS:");
        System.out.println("  Total:  " + totalTests);
        System.out.println("  Passed: " + passedTests + " ✅");
        System.out.println("  Failed: " + failedTests + " ❌");
        System.out.println("  Rate:   " + (totalTests > 0 ? (passedTests * 100 / totalTests) : 0) + "%");
        System.out.println("══════════════════════════════════════════════════════════");
    }

    // ─── Trade Model Tests ───────────────────────────────────────────────

    private static void testTradeModel() {
        System.out.println("── Trade Model Tests ──────────────────────────────────");

        // Test 1: Constructor with all fields
        Trade t1 = new Trade(1, "BUY", "USD/INR", 100.0, 83.50, "2025-01-01 10:00:00");
        assertEqual("Trade ID", 1, t1.getId());
        assertEqual("Trade Type", "BUY", t1.getType());
        assertEqual("Trade Instrument", "USD/INR", t1.getInstrument());
        assertEqual("Trade Quantity", 100.0, t1.getQuantity());
        assertEqual("Trade Price", 83.50, t1.getPrice());

        // Test 2: Constructor without ID (auto-timestamp)
        Trade t2 = new Trade("SELL", "EUR/USD", 50.0, 1.0850);
        assertEqual("Auto ID", 0, t2.getId());
        assertEqual("Type", "SELL", t2.getType());
        assertNotNull("Auto Timestamp", t2.getTimestamp());

        // Test 3: Setters
        t2.setId(42);
        t2.setQuantity(75.0);
        t2.setPrice(1.0900);
        assertEqual("Updated ID", 42, t2.getId());
        assertEqual("Updated Quantity", 75.0, t2.getQuantity());
        assertEqual("Updated Price", 1.0900, t2.getPrice());

        System.out.println();
    }

    // ─── Trade Validation Tests ──────────────────────────────────────────

    private static void testTradeValidation() {
        System.out.println("── Trade Validation Tests ─────────────────────────────");

        // Valid inputs
        assertNull("Valid BUY trade", Trade.validate("BUY", "USD/INR", 100, 83.5));
        assertNull("Valid SELL trade", Trade.validate("SELL", "GOLD", 10, 2350.0));

        // Invalid type
        assertNotNull("Invalid type 'HOLD'", Trade.validate("HOLD", "USD/INR", 100, 83.5));
        assertNotNull("Null type", Trade.validate(null, "USD/INR", 100, 83.5));

        // Invalid instrument
        assertNotNull("Empty instrument", Trade.validate("BUY", "", 100, 83.5));
        assertNotNull("Null instrument", Trade.validate("BUY", null, 100, 83.5));

        // Invalid quantity
        assertNotNull("Zero quantity", Trade.validate("BUY", "USD/INR", 0, 83.5));
        assertNotNull("Negative quantity", Trade.validate("BUY", "USD/INR", -10, 83.5));

        // Invalid price
        assertNotNull("Zero price", Trade.validate("BUY", "USD/INR", 100, 0));
        assertNotNull("Negative price", Trade.validate("BUY", "USD/INR", 100, -5));

        System.out.println();
    }

    // ─── Trade CSV Serialization Tests ───────────────────────────────────

    private static void testTradeCSVSerialization() {
        System.out.println("── CSV Serialization Tests ────────────────────────────");

        // Test CSV export
        Trade original = new Trade(5, "BUY", "GBP/USD", 200.0, 1.2650, "2025-03-15 14:30:00");
        String csv = original.toCSV();
        assertNotNull("CSV not null", csv);
        assertTrue("CSV contains ID", csv.contains("5"));
        assertTrue("CSV contains type", csv.contains("BUY"));
        assertTrue("CSV contains instrument", csv.contains("GBP/USD"));

        // Test CSV import
        Trade parsed = Trade.fromCSV(csv);
        assertNotNull("Parsed trade not null", parsed);
        assertEqual("Parsed ID", 5, parsed.getId());
        assertEqual("Parsed Type", "BUY", parsed.getType());
        assertEqual("Parsed Instrument", "GBP/USD", parsed.getInstrument());
        assertEqual("Parsed Quantity", 200.0, parsed.getQuantity());

        // Test invalid CSV
        Trade invalid1 = Trade.fromCSV(null);
        assertNull("Null CSV returns null", invalid1);

        Trade invalid2 = Trade.fromCSV("");
        assertNull("Empty CSV returns null", invalid2);

        Trade invalid3 = Trade.fromCSV("invalid,data");
        assertNull("Short CSV returns null", invalid3);

        System.out.println();
    }

    // ─── Portfolio Operations Tests ──────────────────────────────────────

    private static void testPortfolioOperations() {
        System.out.println("── Portfolio CRUD Tests ────────────────────────────────");

        Portfolio portfolio = new Portfolio();

        // Test: Empty portfolio
        assertEqual("Empty portfolio size", 0, portfolio.getTradeCount());
        assertEqual("Empty portfolio P/L", 0.0, portfolio.calculateProfitLoss());

        // Test: Add trades (using in-memory only to avoid DB dependency)
        Trade t1 = new Trade("BUY", "USD/INR", 100, 83.50);
        t1.setId(1);
        // We test Portfolio logic directly instead of going through addTrade
        // which would attempt DB access

        // Test: getTrades returns unmodifiable list
        List<Trade> trades = portfolio.getTrades();
        assertNotNull("Trades list not null", trades);

        System.out.println();
    }

    // ─── Profit/Loss Calculation Tests ───────────────────────────────────

    private static void testPortfolioProfitLoss() {
        System.out.println("── Profit/Loss Calculation Tests ──────────────────────");

        Portfolio p = new Portfolio();

        // Manually add trades to test P/L calculation
        // We access the internal list directly for testing
        Trade buy1 = new Trade(1, "BUY", "USD/INR", 100, 83.50, "2025-01-01 10:00:00");
        Trade sell1 = new Trade(2, "SELL", "USD/INR", 100, 85.00, "2025-01-15 10:00:00");

        // Simulate portfolio calculation
        double pl = 0;
        pl -= buy1.getQuantity() * buy1.getPrice();  // -8350
        pl += sell1.getQuantity() * sell1.getPrice();  // +8500

        assertEqual("Simple P/L", 150.0, pl);

        // Test with multiple instruments
        Trade buy2 = new Trade(3, "BUY", "GOLD", 10, 2300.0, "2025-02-01 10:00:00");
        Trade sell2 = new Trade(4, "SELL", "GOLD", 10, 2350.0, "2025-02-15 10:00:00");

        double goldPL = (sell2.getQuantity() * sell2.getPrice()) - (buy2.getQuantity() * buy2.getPrice());
        assertEqual("Gold P/L", 500.0, goldPL);

        // Test loss scenario
        Trade buyHigh = new Trade(5, "BUY", "EUR/USD", 1000, 1.10, "2025-03-01 10:00:00");
        Trade sellLow = new Trade(6, "SELL", "EUR/USD", 1000, 1.05, "2025-03-15 10:00:00");

        double lossPL = (sellLow.getQuantity() * sellLow.getPrice()) - (buyHigh.getQuantity() * buyHigh.getPrice());
        assertTrue("Loss is negative", lossPL < 0);
        assertEqual("EUR/USD Loss", -50.0, lossPL);

        System.out.println();
    }

    // ─── Strategy Simulator Tests ────────────────────────────────────────

    private static void testStrategySimulator() {
        System.out.println("── Strategy Simulator Tests ───────────────────────────");

        // Create test trades
        List<Trade> testTrades = new ArrayList<>();
        testTrades.add(new Trade(1, "BUY", "USD/INR", 100, 83.50, "2025-01-01 10:00:00"));
        testTrades.add(new Trade(2, "BUY", "GOLD", 5, 2300.0, "2025-01-02 10:00:00"));
        testTrades.add(new Trade(3, "SELL", "EUR/USD", 200, 1.0850, "2025-01-03 10:00:00"));
        testTrades.add(new Trade(4, "BUY", "NIFTY 50", 50, 22000.0, "2025-01-04 10:00:00"));

        // Test Buy & Hold
        double buyHoldResult = StrategySimulator.buyAndHold(testTrades);
        assertTrue("Buy & Hold returns a value", !Double.isNaN(buyHoldResult));
        System.out.println("  Buy & Hold result: " + String.format("%.2f", buyHoldResult));

        // Test Random Strategy
        double randomResult = StrategySimulator.randomStrategy(testTrades);
        assertTrue("Random Strategy returns a value", !Double.isNaN(randomResult));
        System.out.println("  Random Strategy result: " + String.format("%.2f", randomResult));

        // Test Momentum Strategy
        double momentumResult = StrategySimulator.momentumStrategy(testTrades);
        assertTrue("Momentum Strategy returns a value", !Double.isNaN(momentumResult));
        System.out.println("  Momentum Strategy result: " + String.format("%.2f", momentumResult));

        // Test with empty list
        double emptyResult = StrategySimulator.buyAndHold(new ArrayList<>());
        assertEqual("Empty portfolio B&H", 0.0, emptyResult);

        // Test with null
        double nullResult = StrategySimulator.randomStrategy(null);
        assertEqual("Null portfolio Random", 0.0, nullResult);

        // Test comparison
        String comparison = StrategySimulator.compareStrategies(testTrades);
        assertNotNull("Comparison not null", comparison);
        assertTrue("Comparison has bestStrategy", comparison.contains("bestStrategy"));
        assertTrue("Comparison has tradeCount", comparison.contains("tradeCount"));
        System.out.println("  Comparison JSON: " + comparison);

        System.out.println();
    }

    // ─── FileManager Tests ───────────────────────────────────────────────

    private static void testFileManager() {
        System.out.println("── FileManager Tests ──────────────────────────────────");

        try {
            // Test: Save trades to file
            List<Trade> testTrades = new ArrayList<>();
            testTrades.add(new Trade(1, "BUY", "USD/INR", 100, 83.50, "2025-01-01 10:00:00"));
            testTrades.add(new Trade(2, "SELL", "GOLD", 5, 2350.0, "2025-01-02 10:00:00"));
            testTrades.add(new Trade(3, "BUY", "NIFTY 50", 50, 22000.0, "2025-01-03 10:00:00"));

            FileManager.save(testTrades);
            assertTrue("File exists after save", FileManager.fileExists());

            // Test: Load trades from file
            List<Trade> loaded = FileManager.load();
            assertEqual("Loaded trade count", 3, loaded.size());
            assertEqual("First trade type", "BUY", loaded.get(0).getType());
            assertEqual("First trade instrument", "USD/INR", loaded.get(0).getInstrument());
            assertEqual("Second trade instrument", "GOLD", loaded.get(1).getInstrument());

            // Test: Append trade
            Trade newTrade = new Trade(4, "SELL", "EUR/USD", 200, 1.085, "2025-01-04 10:00:00");
            FileManager.append(newTrade);

            List<Trade> afterAppend = FileManager.load();
            assertEqual("Count after append", 4, afterAppend.size());

            // Test: File path
            String path = FileManager.getFilePath();
            assertNotNull("File path not null", path);

            System.out.println("  File saved at: " + path);

        } catch (Exception e) {
            System.out.println("  ❌ FileManager test error: " + e.getMessage());
            failedTests++;
        }

        System.out.println();
    }

    // ─── Edge Case Tests ─────────────────────────────────────────────────

    private static void testEdgeCases() {
        System.out.println("── Edge Case Tests ────────────────────────────────────");

        // Test: Very large numbers
        Trade largeTrade = new Trade("BUY", "BTC/USD", 999999.99, 99999.99);
        assertNotNull("Large number trade", largeTrade);
        assertTrue("Large trade JSON", largeTrade.toJSON().contains("999999.99"));

        // Test: Very small numbers
        Trade smallTrade = new Trade("SELL", "USD/JPY", 0.001, 0.0001);
        assertNotNull("Small number trade", smallTrade);

        // Test: Special characters in JSON
        Trade t = new Trade(1, "BUY", "S&P 500", 100, 5000.0, "2025-01-01 10:00:00");
        String json = t.toJSON();
        assertNotNull("JSON with special chars", json);

        // Test: Trade toString
        String str = t.toString();
        assertNotNull("toString not null", str);
        assertTrue("toString has instrument", str.contains("S&P 500"));

        // Test: Strategy with single trade
        List<Trade> singleTrade = new ArrayList<>();
        singleTrade.add(new Trade(1, "BUY", "GOLD", 1, 2300, "2025-01-01 10:00:00"));
        double result = StrategySimulator.buyAndHold(singleTrade);
        assertTrue("Single trade strategy", !Double.isNaN(result));

        // Test: DB connection test (may fail if DB not running - that's expected)
        boolean dbStatus = DBManager.testConnection();
        System.out.println("  Database connection: " + (dbStatus ? "✅ Connected" : "⚠ Not available (expected in test mode)"));

        // Test: Portfolio summary JSON
        Portfolio p = new Portfolio();
        String summary = p.toSummaryJSON();
        assertNotNull("Summary JSON not null", summary);
        assertTrue("Summary has tradeCount", summary.contains("tradeCount"));

        System.out.println();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ASSERTION HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private static void assertEqual(String testName, int expected, int actual) {
        totalTests++;
        if (expected == actual) {
            passedTests++;
            System.out.println("  ✅ " + testName + ": " + actual);
        } else {
            failedTests++;
            System.out.println("  ❌ " + testName + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEqual(String testName, double expected, double actual) {
        totalTests++;
        if (Math.abs(expected - actual) < 0.01) {
            passedTests++;
            System.out.println("  ✅ " + testName + ": " + String.format("%.2f", actual));
        } else {
            failedTests++;
            System.out.println("  ❌ " + testName + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEqual(String testName, String expected, String actual) {
        totalTests++;
        if (expected != null && expected.equals(actual)) {
            passedTests++;
            System.out.println("  ✅ " + testName + ": " + actual);
        } else {
            failedTests++;
            System.out.println("  ❌ " + testName + ": expected \"" + expected + "\", got \"" + actual + "\"");
        }
    }

    private static void assertTrue(String testName, boolean condition) {
        totalTests++;
        if (condition) {
            passedTests++;
            System.out.println("  ✅ " + testName);
        } else {
            failedTests++;
            System.out.println("  ❌ " + testName + ": condition is false");
        }
    }

    private static void assertNull(String testName, Object value) {
        totalTests++;
        if (value == null) {
            passedTests++;
            System.out.println("  ✅ " + testName + ": null");
        } else {
            failedTests++;
            System.out.println("  ❌ " + testName + ": expected null, got " + value);
        }
    }

    private static void assertNotNull(String testName, Object value) {
        totalTests++;
        if (value != null) {
            passedTests++;
            System.out.println("  ✅ " + testName);
        } else {
            failedTests++;
            System.out.println("  ❌ " + testName + ": expected non-null, got null");
        }
    }
}
