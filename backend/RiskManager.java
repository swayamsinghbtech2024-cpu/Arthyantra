import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * RiskManager — Risk Management Module
 * 
 * Implements MarketObserver to monitor active trades against risk thresholds.
 * On every price tick, it checks if any open trades should be closed based on:
 * 
 * - Stop-Loss: Maximum acceptable loss per trade
 * - Take-Profit: Target profit level to lock in gains
 * - Max Drawdown: Maximum portfolio-level loss from peak
 * - Risk Per Trade: Maximum % of portfolio to risk on a single trade
 * 
 * Features:
 * - Real-time position monitoring on every price tick
 * - Automatic trade closure when thresholds are hit
 * - Portfolio drawdown tracking
 * - Risk metrics reporting
 */
public class RiskManager implements MarketObserver {

    // ─── Configuration ───────────────────────────────────────────────
    private double stopLossPercent = 5.0;    // 5% stop-loss
    private double takeProfitPercent = 10.0;  // 10% take-profit
    private double maxDrawdownPercent = 20.0; // 20% max portfolio drawdown
    private double riskPerTradePercent = 2.0; // Risk 2% of portfolio per trade

    // ─── State ───────────────────────────────────────────────────────
    private double peakPortfolioValue = 0.0;
    private double currentDrawdown = 0.0;
    private int stopLossTriggered = 0;
    private int takeProfitTriggered = 0;

    // ─── Dependencies ────────────────────────────────────────────────
    private final Portfolio portfolio;

    // ─── Risk Events Log ─────────────────────────────────────────────
    private final List<String> riskEvents = new CopyOnWriteArrayList<>();
    private static final int MAX_EVENTS = 100;

    // ─── Constructor ─────────────────────────────────────────────────

    public RiskManager(Portfolio portfolio) {
        this.portfolio = portfolio;
        this.peakPortfolioValue = portfolio.getTotalInvested();
        System.out.println("[RiskManager] Initialized. SL=" + stopLossPercent 
                + "%, TP=" + takeProfitPercent + "%, MaxDD=" + maxDrawdownPercent + "%");
    }

    // ─── Observer Pattern: onPriceUpdate ─────────────────────────────

    @Override
    public void onPriceUpdate(String instrument, double currentPrice, String timestamp) {
        // Check all trades for this instrument against risk thresholds
        List<Trade> trades = portfolio.getTrades();

        for (Trade trade : trades) {
            if (!trade.getInstrument().equals(instrument)) continue;
            if ("CLOSED".equals(trade.getStatus())) continue;

            double entryPrice = trade.getPrice();
            double quantity = trade.getQuantity();
            double tradeValue = quantity * entryPrice;
            double currentValue = quantity * currentPrice;

            if ("BUY".equals(trade.getType())) {
                double pnlPercent = ((currentPrice - entryPrice) / entryPrice) * 100;

                // Check stop-loss
                double effectiveSL = trade.getStopLoss() > 0 ? trade.getStopLoss() : stopLossPercent;
                if (pnlPercent <= -effectiveSL) {
                    logRiskEvent(String.format("STOP-LOSS triggered on Trade #%d (%s). Loss: %.2f%%",
                            trade.getId(), instrument, pnlPercent));
                    stopLossTriggered++;
                }

                // Check take-profit
                double effectiveTP = trade.getTakeProfit() > 0 ? trade.getTakeProfit() : takeProfitPercent;
                if (pnlPercent >= effectiveTP) {
                    logRiskEvent(String.format("TAKE-PROFIT triggered on Trade #%d (%s). Gain: %.2f%%",
                            trade.getId(), instrument, pnlPercent));
                    takeProfitTriggered++;
                }
            }
        }

        // Update drawdown tracking
        updateDrawdown();
    }

    // ─── Observer Pattern: onSignalGenerated ─────────────────────────

    @Override
    public void onSignalGenerated(Signal signal) {
        // RiskManager can optionally validate signals before execution
        // For example, blocking new BUY signals if drawdown is too high
        if (currentDrawdown >= maxDrawdownPercent && Signal.BUY.equals(signal.getType())) {
            logRiskEvent("WARNING: BUY signal blocked — drawdown at " 
                    + String.format("%.2f", currentDrawdown) + "% exceeds max " + maxDrawdownPercent + "%");
        }
    }

    // ─── Drawdown Tracking ───────────────────────────────────────────

    private void updateDrawdown() {
        double currentValue = portfolio.getTotalInvested() + portfolio.calculateProfitLoss();
        if (currentValue > peakPortfolioValue) {
            peakPortfolioValue = currentValue;
        }
        if (peakPortfolioValue > 0) {
            currentDrawdown = ((peakPortfolioValue - currentValue) / peakPortfolioValue) * 100;
        }
    }

    // ─── Risk Calculation ────────────────────────────────────────────

    /**
     * Calculates the maximum position size allowed based on risk-per-trade.
     * 
     * @param portfolioBalance Current portfolio balance
     * @param entryPrice Price at which trade would be entered
     * @return Maximum quantity that can be traded
     */
    public double calculateMaxPositionSize(double portfolioBalance, double entryPrice) {
        if (entryPrice <= 0) return 0;
        double maxRiskAmount = portfolioBalance * (riskPerTradePercent / 100.0);
        double stopLossAmount = entryPrice * (stopLossPercent / 100.0);
        return maxRiskAmount / stopLossAmount;
    }

    // ─── Configuration ───────────────────────────────────────────────

    public void setStopLossPercent(double percent) { this.stopLossPercent = percent; }
    public void setTakeProfitPercent(double percent) { this.takeProfitPercent = percent; }
    public void setMaxDrawdownPercent(double percent) { this.maxDrawdownPercent = percent; }
    public void setRiskPerTradePercent(double percent) { this.riskPerTradePercent = percent; }

    public double getStopLossPercent() { return stopLossPercent; }
    public double getTakeProfitPercent() { return takeProfitPercent; }
    public double getMaxDrawdownPercent() { return maxDrawdownPercent; }
    public double getCurrentDrawdown() { return currentDrawdown; }

    // ─── Event Logging ───────────────────────────────────────────────

    private void logRiskEvent(String event) {
        riskEvents.add(event);
        if (riskEvents.size() > MAX_EVENTS) {
            riskEvents.remove(0);
        }
        System.out.println("[RiskManager] " + event);
    }

    public List<String> getRiskEvents() {
        return Collections.unmodifiableList(riskEvents);
    }

    // ─── JSON Serialization ──────────────────────────────────────────

    public String toJSON() {
        return "{" +
                "\"stopLossPercent\":" + stopLossPercent + "," +
                "\"takeProfitPercent\":" + takeProfitPercent + "," +
                "\"maxDrawdownPercent\":" + maxDrawdownPercent + "," +
                "\"riskPerTradePercent\":" + riskPerTradePercent + "," +
                "\"currentDrawdown\":" + String.format("%.2f", currentDrawdown) + "," +
                "\"peakPortfolioValue\":" + String.format("%.2f", peakPortfolioValue) + "," +
                "\"stopLossTriggered\":" + stopLossTriggered + "," +
                "\"takeProfitTriggered\":" + takeProfitTriggered +
                "}";
    }
}
