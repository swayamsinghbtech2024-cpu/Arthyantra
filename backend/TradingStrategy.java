import java.util.List;

/**
 * TradingStrategy Interface — Strategy Pattern
 * 
 * Defines the contract for all trading strategies in the system.
 * Each concrete strategy implements its own signal generation logic
 * based on market data (price history).
 * 
 * Design Pattern: Strategy Pattern
 * - Allows dynamic switching between different trading algorithms
 * - New strategies can be added without modifying existing code
 * 
 * Usage:
 *   TradingStrategy strategy = new MovingAverageStrategy();
 *   Signal signal = strategy.analyze("USD/INR", priceHistory);
 */
public interface TradingStrategy {

    /**
     * Analyzes the given price history for an instrument
     * and generates a trading signal.
     * 
     * @param instrument   The forex pair or instrument name
     * @param priceHistory List of historical prices (oldest first)
     * @return A Signal object with BUY, SELL, or HOLD recommendation
     */
    Signal analyze(String instrument, List<Double> priceHistory);

    /**
     * Returns the human-readable name of this strategy.
     */
    String getName();

    /**
     * Returns a brief description of how this strategy works.
     */
    String getDescription();
}
