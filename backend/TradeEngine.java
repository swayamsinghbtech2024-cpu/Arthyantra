import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TradeEngine — Automated Trade Execution (Observer)
 * 
 * Implements MarketObserver to receive real-time price updates and signals.
 * When a signal with sufficient confidence is received, it automatically
 * executes trades through the Portfolio.
 * 
 * Features:
 * - Auto-executes trades based on strategy signals
 * - Configurable auto-trade enable/disable
 * - Configurable confidence threshold
 * - Default trade quantity
 * - Tracks all signals received (for UI display)
 */
public class TradeEngine implements MarketObserver {

    // ─── Configuration ───────────────────────────────────────────────
    private volatile boolean autoTradeEnabled = false;
    private double confidenceThreshold = 0.6;     // Minimum confidence to auto-trade
    private double defaultQuantity = 100.0;        // Default trade quantity

    // ─── Dependencies ────────────────────────────────────────────────
    private final Portfolio portfolio;

    // ─── Signal Log ──────────────────────────────────────────────────
    private final List<Signal> signalLog = new CopyOnWriteArrayList<>();
    private static final int MAX_SIGNALS = 100;    // Keep last 100 signals

    // ─── Constructor ─────────────────────────────────────────────────

    public TradeEngine(Portfolio portfolio) {
        this.portfolio = portfolio;
        System.out.println("[TradeEngine] Initialized. Auto-trade: " + autoTradeEnabled);
    }

    // ─── Observer Pattern: onPriceUpdate ─────────────────────────────

    @Override
    public void onPriceUpdate(String instrument, double price, String timestamp) {
        // TradeEngine primarily reacts to signals, not raw price updates.
        // Price updates are used by RiskManager for stop-loss checks.
    }

    // ─── Observer Pattern: onSignalGenerated ─────────────────────────

    @Override
    public void onSignalGenerated(Signal signal) {
        // Log the signal
        signalLog.add(signal);
        if (signalLog.size() > MAX_SIGNALS) {
            signalLog.remove(0);
        }

        System.out.println("[TradeEngine] Signal received: " + signal);

        // Auto-execute trade if enabled and confidence is high enough
        if (autoTradeEnabled && signal.getConfidence() >= confidenceThreshold) {
            executeTrade(signal);
        }
    }

    // ─── Trade Execution ─────────────────────────────────────────────

    /**
     * Executes a trade based on the given signal.
     */
    private void executeTrade(Signal signal) {
        try {
            String type = signal.getType(); // BUY or SELL
            if (Signal.HOLD.equals(type)) return;

            Trade trade = new Trade(type, signal.getInstrument(), 
                                    defaultQuantity, signal.getPrice());
            Trade executed = portfolio.addTrade(trade);

            System.out.println("[TradeEngine] AUTO-TRADE executed: " + executed);
        } catch (Exception e) {
            System.out.println("[TradeEngine] Trade execution failed: " + e.getMessage());
        }
    }

    // ─── Configuration ───────────────────────────────────────────────

    public void setAutoTradeEnabled(boolean enabled) {
        this.autoTradeEnabled = enabled;
        System.out.println("[TradeEngine] Auto-trade " + (enabled ? "ENABLED" : "DISABLED"));
    }

    public boolean isAutoTradeEnabled() {
        return autoTradeEnabled;
    }

    public void setConfidenceThreshold(double threshold) {
        this.confidenceThreshold = Math.min(1.0, Math.max(0.0, threshold));
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setDefaultQuantity(double quantity) {
        this.defaultQuantity = quantity;
    }

    // ─── Signal Access ───────────────────────────────────────────────

    /**
     * Returns the recent signal log.
     */
    public List<Signal> getSignalLog() {
        return Collections.unmodifiableList(signalLog);
    }

    /**
     * Returns the signal log as a JSON array.
     */
    public String signalLogToJSON() {
        StringBuilder sb = new StringBuilder("[");
        int start = Math.max(0, signalLog.size() - 20); // Last 20 signals
        for (int i = start; i < signalLog.size(); i++) {
            if (i > start) sb.append(",");
            sb.append(signalLog.get(i).toJSON());
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Returns engine status as JSON.
     */
    public String toJSON() {
        return "{" +
                "\"autoTradeEnabled\":" + autoTradeEnabled + "," +
                "\"confidenceThreshold\":" + confidenceThreshold + "," +
                "\"defaultQuantity\":" + defaultQuantity + "," +
                "\"totalSignals\":" + signalLog.size() +
                "}";
    }
}
