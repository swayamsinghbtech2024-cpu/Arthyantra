import java.util.*;

/**
 * BacktestEngine — Historical Backtesting Module
 * 
 * Evaluates trading strategies against mock historical data
 * to assess their performance before live deployment.
 * 
 * Features:
 * - Generates mock historical data programmatically
 * - Runs any TradingStrategy against historical data
 * - Calculates performance metrics: total P&L, win rate, max drawdown
 * - Supports comparing multiple strategies side-by-side
 * - Produces detailed JSON results for the frontend
 */
public class BacktestEngine {

    // ─── Backtest Result Inner Class ─────────────────────────────────

    public static class BacktestResult {
        public String strategyName;
        public String instrument;
        public int totalSignals;
        public int buySignals;
        public int sellSignals;
        public int holdSignals;
        public double totalProfitLoss;
        public int winningTrades;
        public int losingTrades;
        public double winRate;
        public double maxDrawdown;
        public double sharpeRatio;
        public int dataPoints;

        public String toJSON() {
            return "{" +
                    "\"strategyName\":\"" + strategyName + "\"," +
                    "\"instrument\":\"" + instrument + "\"," +
                    "\"totalSignals\":" + totalSignals + "," +
                    "\"buySignals\":" + buySignals + "," +
                    "\"sellSignals\":" + sellSignals + "," +
                    "\"holdSignals\":" + holdSignals + "," +
                    "\"totalProfitLoss\":" + String.format("%.2f", totalProfitLoss) + "," +
                    "\"winningTrades\":" + winningTrades + "," +
                    "\"losingTrades\":" + losingTrades + "," +
                    "\"winRate\":" + String.format("%.2f", winRate) + "," +
                    "\"maxDrawdown\":" + String.format("%.2f", maxDrawdown) + "," +
                    "\"sharpeRatio\":" + String.format("%.2f", sharpeRatio) + "," +
                    "\"dataPoints\":" + dataPoints +
                    "}";
        }
    }

    // ─── Run Backtest ────────────────────────────────────────────────

    /**
     * Runs a single strategy backtest against mock historical data.
     * 
     * @param strategy   The strategy to test
     * @param instrument The instrument to simulate
     * @param dataPoints Number of historical data points
     * @return BacktestResult with performance metrics
     */
    public static BacktestResult runBacktest(TradingStrategy strategy, 
                                              String instrument, 
                                              int dataPoints) {
        MarketSimulator simulator = new MarketSimulator();
        List<Double> historicalData = simulator.generateHistoricalData(instrument, dataPoints);

        BacktestResult result = new BacktestResult();
        result.strategyName = strategy.getName();
        result.instrument = instrument;
        result.dataPoints = dataPoints;

        double balance = 100000.0;  // Starting balance
        double position = 0.0;     // Current position quantity
        double entryPrice = 0.0;   // Price at which we entered
        double peakBalance = balance;
        double maxDrawdown = 0.0;

        int wins = 0;
        int losses = 0;
        List<Double> returns = new ArrayList<>();

        // Slide a window through historical data
        int windowSize = 30; // Minimum data needed for strategy warm-up
        for (int i = windowSize; i < historicalData.size(); i++) {
            // Get the price window up to current point
            List<Double> window = historicalData.subList(0, i + 1);
            double currentPrice = historicalData.get(i);

            // Generate signal
            Signal signal = strategy.analyze(instrument, window);
            result.totalSignals++;

            switch (signal.getType()) {
                case "BUY":
                    result.buySignals++;
                    if (position == 0) {
                        // Open a long position
                        position = balance / currentPrice;
                        entryPrice = currentPrice;
                    }
                    break;

                case "SELL":
                    result.sellSignals++;
                    if (position > 0) {
                        // Close the position
                        double exitValue = position * currentPrice;
                        double pnl = exitValue - (position * entryPrice);
                        balance = exitValue;
                        returns.add(pnl);

                        if (pnl > 0) wins++;
                        else losses++;

                        position = 0;
                        entryPrice = 0;
                    }
                    break;

                case "HOLD":
                    result.holdSignals++;
                    break;
            }

            // Track drawdown
            double currentTotal = position > 0 ? position * currentPrice : balance;
            if (currentTotal > peakBalance) peakBalance = currentTotal;
            double dd = ((peakBalance - currentTotal) / peakBalance) * 100;
            if (dd > maxDrawdown) maxDrawdown = dd;
        }

        // Close any remaining open position
        if (position > 0) {
            double lastPrice = historicalData.get(historicalData.size() - 1);
            double exitValue = position * lastPrice;
            double pnl = exitValue - (position * entryPrice);
            balance = exitValue;
            returns.add(pnl);
            if (pnl > 0) wins++;
            else losses++;
        }

        result.totalProfitLoss = balance - 100000.0;
        result.winningTrades = wins;
        result.losingTrades = losses;
        result.winRate = (wins + losses) > 0 ? (wins * 100.0) / (wins + losses) : 0;
        result.maxDrawdown = maxDrawdown;
        result.sharpeRatio = calculateSharpe(returns);

        return result;
    }

    // ─── Compare Strategies ──────────────────────────────────────────

    /**
     * Runs backtests on multiple strategies and returns comparison.
     */
    public static String compareStrategies(String instrument, int dataPoints) {
        TradingStrategy[] strategies = {
            new MovingAverageStrategy(),
            new MovingAverageStrategy(10, 30),
            new RSIStrategy(),
            new RSIStrategy(14, 25, 75)
        };

        StringBuilder sb = new StringBuilder("{\"results\":[");
        String bestStrategy = "";
        double bestPL = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < strategies.length; i++) {
            BacktestResult result = runBacktest(strategies[i], instrument, dataPoints);
            if (i > 0) sb.append(",");
            sb.append(result.toJSON());

            if (result.totalProfitLoss > bestPL) {
                bestPL = result.totalProfitLoss;
                bestStrategy = result.strategyName;
            }
        }

        sb.append("],\"bestStrategy\":\"").append(bestStrategy).append("\"");
        sb.append(",\"bestProfitLoss\":").append(String.format("%.2f", bestPL));
        sb.append(",\"instrument\":\"").append(instrument).append("\"");
        sb.append(",\"dataPoints\":").append(dataPoints);
        sb.append("}");

        return sb.toString();
    }

    // ─── Sharpe Ratio Calculation ────────────────────────────────────

    /**
     * Calculates the Sharpe Ratio from a list of trade returns.
     * Sharpe = mean(returns) / stdev(returns)
     */
    private static double calculateSharpe(List<Double> returns) {
        if (returns.isEmpty() || returns.size() < 2) return 0.0;

        double sum = 0;
        for (double r : returns) sum += r;
        double mean = sum / returns.size();

        double varianceSum = 0;
        for (double r : returns) varianceSum += (r - mean) * (r - mean);
        double stdev = Math.sqrt(varianceSum / (returns.size() - 1));

        if (stdev == 0) return 0.0;
        return mean / stdev;
    }
}
