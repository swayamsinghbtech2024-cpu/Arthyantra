import java.util.*;

/**
 * MovingAverageStrategy — Concrete Strategy (Strategy Pattern)
 * 
 * Implements a Simple Moving Average (SMA) crossover strategy.
 * Uses a short-term SMA and a long-term SMA to generate signals:
 * 
 * - BUY:  When the short SMA crosses ABOVE the long SMA (bullish crossover)
 * - SELL: When the short SMA crosses BELOW the long SMA (bearish crossover)
 * - HOLD: When no crossover is detected
 * 
 * Parameters:
 * - Short period: 5 (fast-moving average)
 * - Long period:  20 (slow-moving average)
 */
public class MovingAverageStrategy implements TradingStrategy {

    private final int shortPeriod;
    private final int longPeriod;

    // ─── Constructor ─────────────────────────────────────────────────

    public MovingAverageStrategy() {
        this.shortPeriod = 5;
        this.longPeriod = 20;
    }

    public MovingAverageStrategy(int shortPeriod, int longPeriod) {
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
    }

    // ─── Strategy Pattern: analyze() ─────────────────────────────────

    @Override
    public Signal analyze(String instrument, List<Double> priceHistory) {
        if (priceHistory == null || priceHistory.size() < longPeriod + 1) {
            return new Signal(Signal.HOLD, instrument,
                    priceHistory != null && !priceHistory.isEmpty() 
                        ? priceHistory.get(priceHistory.size() - 1) : 0.0,
                    0.1, getName(),
                    "Insufficient data (" + (priceHistory != null ? priceHistory.size() : 0) 
                        + " points, need " + (longPeriod + 1) + ")");
        }

        int size = priceHistory.size();
        double currentPrice = priceHistory.get(size - 1);

        // Calculate current SMAs
        double currentShortSMA = calculateSMA(priceHistory, size - 1, shortPeriod);
        double currentLongSMA = calculateSMA(priceHistory, size - 1, longPeriod);

        // Calculate previous SMAs (for crossover detection)
        double prevShortSMA = calculateSMA(priceHistory, size - 2, shortPeriod);
        double prevLongSMA = calculateSMA(priceHistory, size - 2, longPeriod);

        // Detect crossover
        boolean currentShortAbove = currentShortSMA > currentLongSMA;
        boolean prevShortAbove = prevShortSMA > prevLongSMA;

        // Calculate confidence based on the magnitude of the crossover
        double spread = Math.abs(currentShortSMA - currentLongSMA);
        double avgPrice = (currentShortSMA + currentLongSMA) / 2.0;
        double spreadPercent = (spread / avgPrice) * 100;
        double confidence = Math.min(0.95, 0.5 + spreadPercent * 0.1);

        if (currentShortAbove && !prevShortAbove) {
            // Bullish crossover — BUY signal
            return new Signal(Signal.BUY, instrument, currentPrice, confidence, getName(),
                    String.format("SMA(%d) crossed above SMA(%d). Short=%.4f, Long=%.4f",
                            shortPeriod, longPeriod, currentShortSMA, currentLongSMA));
        } else if (!currentShortAbove && prevShortAbove) {
            // Bearish crossover — SELL signal
            return new Signal(Signal.SELL, instrument, currentPrice, confidence, getName(),
                    String.format("SMA(%d) crossed below SMA(%d). Short=%.4f, Long=%.4f",
                            shortPeriod, longPeriod, currentShortSMA, currentLongSMA));
        } else {
            // No crossover — HOLD
            String trend = currentShortAbove ? "bullish" : "bearish";
            return new Signal(Signal.HOLD, instrument, currentPrice, 0.3, getName(),
                    String.format("No crossover. Trend is %s. Short=%.4f, Long=%.4f",
                            trend, currentShortSMA, currentLongSMA));
        }
    }

    // ─── SMA Calculation ─────────────────────────────────────────────

    /**
     * Calculates the Simple Moving Average ending at the given index.
     */
    private double calculateSMA(List<Double> prices, int endIndex, int period) {
        double sum = 0.0;
        int start = Math.max(0, endIndex - period + 1);
        int count = endIndex - start + 1;
        for (int i = start; i <= endIndex; i++) {
            sum += prices.get(i);
        }
        return sum / count;
    }

    // ─── Metadata ────────────────────────────────────────────────────

    @Override
    public String getName() {
        return "Moving Average (" + shortPeriod + "/" + longPeriod + ")";
    }

    @Override
    public String getDescription() {
        return "Uses SMA crossover between " + shortPeriod + "-period and " 
                + longPeriod + "-period moving averages to detect trend changes.";
    }
}
