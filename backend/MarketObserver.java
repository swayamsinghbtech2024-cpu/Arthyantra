/**
 * MarketObserver Interface — Observer Pattern
 * 
 * Defines the contract for objects that want to be notified
 * when market data changes (new price ticks).
 * 
 * Design Pattern: Observer Pattern
 * - MarketSimulator is the Subject (Observable)
 * - TradeEngine, SignalLogger, and UI are Observers
 * 
 * Observers register with the MarketSimulator and receive:
 * 1. Price updates via onPriceUpdate()
 * 2. Signal notifications via onSignalGenerated()
 */
public interface MarketObserver {

    /**
     * Called when a new market price tick is available.
     * 
     * @param instrument The instrument that has a new price
     * @param price      The new price value
     * @param timestamp  When the price was generated
     */
    void onPriceUpdate(String instrument, double price, String timestamp);

    /**
     * Called when a strategy generates a new trading signal.
     * 
     * @param signal The generated signal (BUY/SELL/HOLD)
     */
    void onSignalGenerated(Signal signal);
}
