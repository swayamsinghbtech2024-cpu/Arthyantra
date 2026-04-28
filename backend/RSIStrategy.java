import java.util.*;

/**
 * RSIStrategy — Concrete Strategy (Strategy Pattern)
 * 
 * Implements the Relative Strength Index (RSI) strategy.
 * RSI measures the speed and change of price movements on a 0-100 scale.
 * 
 * - BUY:  When RSI drops below 30 (oversold condition)
 * - SELL: When RSI rises above 70 (overbought condition)
 * - HOLD: When RSI is between 30 and 70 (neutral zone)
 * 
 * Parameters:
 * - Period: 14 (standard RSI period)
 * - Oversold threshold: 30
 * - Overbought threshold: 70
 */
public class RSIStrategy implements TradingStrategy {

    private final int period;
    private final double oversoldThreshold;
    private final double overboughtThreshold;

    // ─── Constructor ─────────────────────────────────────────────────

    public RSIStrategy() {
        this.period = 14;
        this.oversoldThreshold = 30.0;
        this.overboughtThreshold = 70.0;
    }

    public RSIStrategy(int period, double oversold, double overbought) {
        this.period = period;
        this.oversoldThreshold = oversold;
        this.overboughtThreshold = overbought;
    }

    // ─── Strategy Pattern: analyze() ─────────────────────────────────

    @Override
    public Signal analyze(String instrument, List<Double> priceHistory) {
        if (priceHistory == null || priceHistory.size() < period + 2) {
            return new Signal(Signal.HOLD, instrument,
                    priceHistory != null && !priceHistory.isEmpty()
                        ? priceHistory.get(priceHistory.size() - 1) : 0.0,
                    0.1, getName(),
                    "Insufficient data (" + (priceHistory != null ? priceHistory.size() : 0) 
                        + " points, need " + (period + 2) + ")");
        }

        int size = priceHistory.size();
        double currentPrice = priceHistory.get(size - 1);
        double rsi = calculateRSI(priceHistory);

        // Calculate confidence based on how extreme the RSI is
        double confidence;
        if (rsi <= oversoldThreshold) {
            confidence = Math.min(0.95, 0.5 + (oversoldThreshold - rsi) * 0.015);
            return new Signal(Signal.BUY, instrument, currentPrice, confidence, getName(),
                    String.format("RSI = %.2f (oversold below %.0f). Potential reversal upward.",
                            rsi, oversoldThreshold));
        } else if (rsi >= overboughtThreshold) {
            confidence = Math.min(0.95, 0.5 + (rsi - overboughtThreshold) * 0.015);
            return new Signal(Signal.SELL, instrument, currentPrice, confidence, getName(),
                    String.format("RSI = %.2f (overbought above %.0f). Potential reversal downward.",
                            rsi, overboughtThreshold));
        } else {
            // Neutral zone
            confidence = 0.3;
            String zone;
            if (rsi < 45) zone = "leaning oversold";
            else if (rsi > 55) zone = "leaning overbought";
            else zone = "neutral";

            return new Signal(Signal.HOLD, instrument, currentPrice, confidence, getName(),
                    String.format("RSI = %.2f (%s zone). No action required.", rsi, zone));
        }
    }

    // ─── RSI Calculation ─────────────────────────────────────────────

    /**
     * Calculates the RSI using the standard Wilder's smoothing method.
     * 
     * RSI = 100 - (100 / (1 + RS))
     * RS = Average Gain / Average Loss over the period
     */
    private double calculateRSI(List<Double> prices) {
        int size = prices.size();

        // Calculate initial average gain and loss
        double avgGain = 0.0;
        double avgLoss = 0.0;

        for (int i = size - period; i < size; i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) {
                avgGain += change;
            } else {
                avgLoss += Math.abs(change);
            }
        }

        avgGain /= period;
        avgLoss /= period;

        // Avoid division by zero
        if (avgLoss == 0) {
            return 100.0; // All gains, no losses = max RSI
        }

        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    // ─── Metadata ────────────────────────────────────────────────────

    @Override
    public String getName() {
        return "RSI (" + period + ")";
    }

    @Override
    public String getDescription() {
        return "Relative Strength Index with " + period + "-period lookback. "
                + "Buy when RSI < " + (int) oversoldThreshold 
                + ", Sell when RSI > " + (int) overboughtThreshold + ".";
    }
}
