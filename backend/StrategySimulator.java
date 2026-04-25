import java.util.*;

/**
 * StrategySimulator - Strategy Simulation Engine
 * 
 * Implements multiple trading strategies to simulate potential outcomes
 * based on the current portfolio data. Provides comparison analysis
 * between different strategies.
 * 
 * Strategies Implemented:
 * 1. Buy & Hold - Classic long-term investment approach
 * 2. Random Strategy - Monte Carlo style random decision making
 * 3. Momentum Strategy - Trend-following based on price movement
 */
public class StrategySimulator {

    // ─── Buy & Hold Strategy ─────────────────────────────────────────────

    /**
     * Simulates a Buy & Hold strategy.
     * 
     * Logic: Assumes all assets are bought at their trade prices and held.
     * Profit is calculated assuming a market movement of +8% to +15% 
     * (typical annual forex/stock returns), using instrument-specific multipliers.
     * 
     * @param trades List of trades in the portfolio
     * @return Simulated profit/loss from the strategy
     */
    public static double buyAndHold(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0.0;
        }

        double totalInvested = 0.0;
        double simulatedValue = 0.0;

        for (Trade trade : trades) {
            double tradeValue = trade.getQuantity() * trade.getPrice();

            if ("BUY".equals(trade.getType())) {
                totalInvested += tradeValue;

                // Simulate market appreciation based on instrument type
                double growthRate = getGrowthRate(trade.getInstrument());
                simulatedValue += tradeValue * (1 + growthRate);
            } else {
                // SELL trades: profit already realized
                simulatedValue += tradeValue;
                totalInvested += tradeValue * 0.85; // Assume bought at 15% less
            }
        }

        return simulatedValue - totalInvested;
    }

    // ─── Random Strategy ─────────────────────────────────────────────────

    /**
     * Simulates a Random Strategy (Monte Carlo approach).
     * 
     * Logic: For each trade, randomly decides whether to hold or exit.
     * Uses random multipliers to simulate unpredictable market behavior.
     * Runs multiple iterations and returns the average result.
     * 
     * @param trades List of trades in the portfolio
     * @return Average simulated profit/loss from multiple random runs
     */
    public static double randomStrategy(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0.0;
        }

        Random random = new Random(42); // Fixed seed for reproducible results
        int iterations = 100;           // Number of Monte Carlo iterations
        double totalProfit = 0.0;

        for (int i = 0; i < iterations; i++) {
            double iterationProfit = 0.0;

            for (Trade trade : trades) {
                double tradeValue = trade.getQuantity() * trade.getPrice();

                // Random decision: hold (+gain) or exit (-loss)
                if (random.nextBoolean()) {
                    // Positive outcome: gain between 2% and 20%
                    double gain = 0.02 + (random.nextDouble() * 0.18);
                    iterationProfit += tradeValue * gain;
                } else {
                    // Negative outcome: loss between 1% and 12%
                    double loss = 0.01 + (random.nextDouble() * 0.11);
                    iterationProfit -= tradeValue * loss;
                }
            }

            totalProfit += iterationProfit;
        }

        // Return average profit across all iterations
        return totalProfit / iterations;
    }

    // ─── Momentum Strategy ───────────────────────────────────────────────

    /**
     * Simulates a Momentum Strategy.
     * 
     * Logic: Follows the trend of existing trades. If there are more BUY
     * trades for an instrument, assumes bullish momentum and projects gains.
     * If more SELL trades, assumes bearish momentum and projects losses.
     * 
     * @param trades List of trades in the portfolio
     * @return Simulated profit/loss from momentum-based decisions
     */
    public static double momentumStrategy(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0.0;
        }

        // Count BUY vs SELL trades per instrument
        Map<String, int[]> sentimentMap = new HashMap<>(); // [buyCount, sellCount]
        Map<String, Double> valueMap = new HashMap<>();     // total value per instrument

        for (Trade trade : trades) {
            String inst = trade.getInstrument();
            double tradeValue = trade.getQuantity() * trade.getPrice();

            sentimentMap.putIfAbsent(inst, new int[]{0, 0});
            valueMap.putIfAbsent(inst, 0.0);

            if ("BUY".equals(trade.getType())) {
                sentimentMap.get(inst)[0]++;
            } else {
                sentimentMap.get(inst)[1]++;
            }
            valueMap.put(inst, valueMap.get(inst) + tradeValue);
        }

        // Calculate momentum-based profit
        double totalProfit = 0.0;

        for (Map.Entry<String, int[]> entry : sentimentMap.entrySet()) {
            String instrument = entry.getKey();
            int[] counts = entry.getValue();
            double value = valueMap.get(instrument);

            int buyCount = counts[0];
            int sellCount = counts[1];

            if (buyCount > sellCount) {
                // Bullish momentum: project 12% gain
                totalProfit += value * 0.12;
            } else if (sellCount > buyCount) {
                // Bearish momentum: project 5% loss
                totalProfit -= value * 0.05;
            } else {
                // Neutral: project 2% gain (market drift)
                totalProfit += value * 0.02;
            }
        }

        return totalProfit;
    }

    // ─── Strategy Comparison ─────────────────────────────────────────────

    /**
     * Runs all strategies and returns a comparison result as JSON.
     * Identifies the best-performing strategy.
     * 
     * @param trades List of trades to simulate
     * @return JSON string containing results from all strategies
     */
    public static String compareStrategies(List<Trade> trades) {
        double buyHoldResult = buyAndHold(trades);
        double randomResult = randomStrategy(trades);
        double momentumResult = momentumStrategy(trades);

        // Determine the best strategy
        String bestStrategy;
        double bestProfit;

        if (buyHoldResult >= randomResult && buyHoldResult >= momentumResult) {
            bestStrategy = "Buy & Hold";
            bestProfit = buyHoldResult;
        } else if (randomResult >= buyHoldResult && randomResult >= momentumResult) {
            bestStrategy = "Random Strategy";
            bestProfit = randomResult;
        } else {
            bestStrategy = "Momentum Strategy";
            bestProfit = momentumResult;
        }

        // Build JSON response
        return "{" +
                "\"buyAndHold\":" + String.format("%.2f", buyHoldResult) + "," +
                "\"randomStrategy\":" + String.format("%.2f", randomResult) + "," +
                "\"momentumStrategy\":" + String.format("%.2f", momentumResult) + "," +
                "\"bestStrategy\":\"" + bestStrategy + "\"," +
                "\"bestProfit\":" + String.format("%.2f", bestProfit) + "," +
                "\"tradeCount\":" + (trades != null ? trades.size() : 0) +
                "}";
    }

    // ─── Helper Methods ──────────────────────────────────────────────────

    /**
     * Returns an estimated growth rate based on instrument type.
     * Used by the Buy & Hold strategy for realistic simulation.
     */
    private static double getGrowthRate(String instrument) {
        if (instrument == null) return 0.05;

        // Crypto: high volatility
        if (instrument.contains("BTC") || instrument.contains("ETH") ||
            instrument.contains("BITCOIN") || instrument.contains("ETHEREUM")) {
            return 0.25; // 25% potential growth
        }

        // Commodities: moderate
        if (instrument.contains("GOLD") || instrument.contains("SILVER") ||
            instrument.contains("CRUDE")) {
            return 0.08; // 8% growth
        }

        // Indices: stable growth
        if (instrument.contains("NIFTY") || instrument.contains("SENSEX") ||
            instrument.contains("NASDAQ") || instrument.contains("S&P") ||
            instrument.contains("DOW") || instrument.contains("FTSE") ||
            instrument.contains("DAX") || instrument.contains("HANG")) {
            return 0.12; // 12% growth
        }

        // Forex: lower volatility
        return 0.05; // 5% growth for forex pairs
    }
}