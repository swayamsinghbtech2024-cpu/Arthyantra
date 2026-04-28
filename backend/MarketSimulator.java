import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MarketSimulator — Market Simulation Engine (Observer Pattern Subject)
 * 
 * Simulates real-time Forex price movements using random walk with drift.
 * Acts as the SUBJECT in the Observer Pattern — maintains a list of observers
 * and notifies them on every price tick.
 * 
 * Features:
 * - Generates realistic price movements with volatility
 * - Supports multiple instruments simultaneously
 * - Runs analysis using the active TradingStrategy on each tick
 * - Notifies observers of both price updates and generated signals
 * - Supports historical replay (backtesting mode)
 * 
 * Tick interval: 2 seconds (configurable)
 */
public class MarketSimulator {

    // ─── Observer Pattern: list of registered observers ──────────────
    private final List<MarketObserver> observers = new CopyOnWriteArrayList<>();

    // ─── Price data ──────────────────────────────────────────────────
    private final Map<String, List<Double>> priceHistory = new ConcurrentHashMap<>();
    private final Map<String, Double> currentPrices = new ConcurrentHashMap<>();

    // ─── Strategy (Strategy Pattern integration) ─────────────────────
    private volatile TradingStrategy activeStrategy;

    // ─── Simulation state ────────────────────────────────────────────
    private volatile boolean running = false;
    private ScheduledExecutorService scheduler;
    private final Random random = new Random();
    private int tickCount = 0;

    // ─── Configuration ───────────────────────────────────────────────
    private static final int TICK_INTERVAL_MS = 2000;  // 2 seconds per tick
    private static final int MAX_HISTORY = 200;        // Keep last 200 price points
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Instrument base prices and volatilities
    private static final String[][] INSTRUMENTS = {
        {"USD/INR", "83.50", "0.0008"},
        {"EUR/USD", "1.0850", "0.0006"},
        {"GBP/USD", "1.2650", "0.0007"},
        {"USD/JPY", "149.50", "0.0009"},
        {"GOLD",    "2350.00", "0.0012"},
        {"NIFTY 50","22500.00","0.0010"}
    };

    // ─── Constructor ─────────────────────────────────────────────────

    public MarketSimulator() {
        this.activeStrategy = new MovingAverageStrategy();
        initializePrices();
    }

    // ─── Observer Pattern Methods ────────────────────────────────────

    /**
     * Registers an observer to receive price and signal notifications.
     */
    public void addObserver(MarketObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
            System.out.println("[MarketSimulator] Observer registered: " + observer.getClass().getSimpleName());
        }
    }

    /**
     * Removes a registered observer.
     */
    public void removeObserver(MarketObserver observer) {
        observers.remove(observer);
    }

    /**
     * Notifies all observers of a price update.
     */
    private void notifyPriceUpdate(String instrument, double price, String timestamp) {
        for (MarketObserver observer : observers) {
            try {
                observer.onPriceUpdate(instrument, price, timestamp);
            } catch (Exception e) {
                System.out.println("[MarketSimulator] Observer error: " + e.getMessage());
            }
        }
    }

    /**
     * Notifies all observers of a new signal.
     */
    private void notifySignal(Signal signal) {
        for (MarketObserver observer : observers) {
            try {
                observer.onSignalGenerated(signal);
            } catch (Exception e) {
                System.out.println("[MarketSimulator] Observer error: " + e.getMessage());
            }
        }
    }

    // ─── Simulation Control ──────────────────────────────────────────

    /**
     * Starts the market simulation in a background thread.
     */
    public void start() {
        if (running) {
            System.out.println("[MarketSimulator] Already running.");
            return;
        }
        running = true;
        tickCount = 0;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MarketSimulator-Thread");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, 0, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        System.out.println("[MarketSimulator] Simulation started (tick every " + TICK_INTERVAL_MS + "ms)");
    }

    /**
     * Stops the market simulation.
     */
    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
        System.out.println("[MarketSimulator] Simulation stopped after " + tickCount + " ticks.");
    }

    public boolean isRunning() {
        return running;
    }

    // ─── Strategy Switching (Strategy Pattern) ───────────────────────

    /**
     * Dynamically switches the active trading strategy.
     */
    public void setStrategy(TradingStrategy strategy) {
        this.activeStrategy = strategy;
        System.out.println("[MarketSimulator] Strategy switched to: " + strategy.getName());
    }

    public TradingStrategy getActiveStrategy() {
        return activeStrategy;
    }

    // ─── Price Access ────────────────────────────────────────────────

    public Map<String, Double> getCurrentPrices() {
        return Collections.unmodifiableMap(currentPrices);
    }

    public List<Double> getPriceHistory(String instrument) {
        return priceHistory.getOrDefault(instrument, Collections.emptyList());
    }

    public Map<String, List<Double>> getAllPriceHistory() {
        return Collections.unmodifiableMap(priceHistory);
    }

    // ─── Simulation Tick ─────────────────────────────────────────────

    /**
     * Executes one simulation tick:
     * 1. Updates prices for all instruments using random walk
     * 2. Runs the active strategy against updated prices
     * 3. Notifies all observers of price updates and signals
     */
    private void tick() {
        try {
            tickCount++;
            String timestamp = LocalDateTime.now().format(FORMATTER);

            for (String[] inst : INSTRUMENTS) {
                String name = inst[0];
                double volatility = Double.parseDouble(inst[2]);

                // Random walk with mean reversion
                double currentPrice = currentPrices.get(name);
                double basePrice = Double.parseDouble(inst[1]);
                double meanReversion = (basePrice - currentPrice) * 0.001;
                double change = currentPrice * volatility * (random.nextGaussian()) + meanReversion;
                double newPrice = Math.max(currentPrice * 0.95, currentPrice + change);

                // Round to appropriate precision
                if (newPrice > 100) {
                    newPrice = Math.round(newPrice * 100.0) / 100.0;
                } else {
                    newPrice = Math.round(newPrice * 10000.0) / 10000.0;
                }

                // Update state
                currentPrices.put(name, newPrice);
                List<Double> history = priceHistory.get(name);
                history.add(newPrice);
                if (history.size() > MAX_HISTORY) {
                    history.remove(0);
                }

                // Notify observers of price update
                notifyPriceUpdate(name, newPrice, timestamp);

                // Run strategy analysis
                if (activeStrategy != null) {
                    Signal signal = activeStrategy.analyze(name, history);
                    if (signal != null && !Signal.HOLD.equals(signal.getType())) {
                        notifySignal(signal);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[MarketSimulator] Tick error: " + e.getMessage());
        }
    }

    // ─── Initialization ──────────────────────────────────────────────

    /**
     * Initializes price data with base prices and generates
     * initial history using random walk for strategy warm-up.
     */
    private void initializePrices() {
        for (String[] inst : INSTRUMENTS) {
            String name = inst[0];
            double basePrice = Double.parseDouble(inst[1]);
            double volatility = Double.parseDouble(inst[2]);

            currentPrices.put(name, basePrice);

            // Generate 50 points of initial history for strategy warm-up
            List<Double> history = new ArrayList<>();
            double price = basePrice;
            for (int i = 0; i < 50; i++) {
                double change = price * volatility * random.nextGaussian();
                price = Math.max(price * 0.9, price + change);
                if (price > 100) {
                    price = Math.round(price * 100.0) / 100.0;
                } else {
                    price = Math.round(price * 10000.0) / 10000.0;
                }
                history.add(price);
            }
            currentPrices.put(name, price);
            priceHistory.put(name, new ArrayList<>(history));
        }
        System.out.println("[MarketSimulator] Initialized " + INSTRUMENTS.length + " instruments with warm-up data.");
    }

    // ─── Backtesting Support ─────────────────────────────────────────

    /**
     * Generates mock historical data for backtesting.
     * Creates n data points using random walk from the instrument's base price.
     * 
     * @param instrument The instrument name
     * @param dataPoints Number of historical data points to generate
     * @return List of simulated historical prices
     */
    public List<Double> generateHistoricalData(String instrument, int dataPoints) {
        double basePrice = 83.50; // Default
        double volatility = 0.0008;

        for (String[] inst : INSTRUMENTS) {
            if (inst[0].equals(instrument)) {
                basePrice = Double.parseDouble(inst[1]);
                volatility = Double.parseDouble(inst[2]);
                break;
            }
        }

        List<Double> data = new ArrayList<>();
        double price = basePrice;
        Random histRandom = new Random(42); // Fixed seed for reproducibility

        for (int i = 0; i < dataPoints; i++) {
            double change = price * volatility * histRandom.nextGaussian();
            // Add slight upward drift
            change += price * 0.00005;
            price = Math.max(price * 0.8, price + change);

            if (price > 100) {
                price = Math.round(price * 100.0) / 100.0;
            } else {
                price = Math.round(price * 10000.0) / 10000.0;
            }
            data.add(price);
        }

        return data;
    }

    /**
     * Returns current prices as a JSON string for the SSE stream.
     */
    public String toJSON() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> entry : currentPrices.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(String.format("%.4f", entry.getValue()));
            first = false;
        }
        sb.append(",\"tick\":").append(tickCount);
        sb.append(",\"running\":").append(running);
        sb.append(",\"strategy\":\"").append(activeStrategy != null ? activeStrategy.getName() : "None").append("\"");
        sb.append("}");
        return sb.toString();
    }
}
