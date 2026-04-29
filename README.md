# Arthyantra - Forex Trading Platform & Simulator

![Arthyantra Dashboard](dashboard.png)

Arthyantra is a comprehensive, real-time Forex trading simulator and portfolio management platform. It allows users to simulate live market conditions, evaluate automated trading strategies, backtest historical data, and manage risk parameters—all within a modern, data-dense interface inspired by professional trading terminals.

## 🚀 Key Features

*   **Real-time Market Simulation:** Live price streaming for 6 major instruments (USD/INR, EUR/USD, GBP/USD, USD/JPY, GOLD, NIFTY 50) using Server-Sent Events (SSE).
*   **Strategy Engine:** Supports automated trading strategies using the Strategy Pattern. Currently includes Simple Moving Average (SMA) crossover and Relative Strength Index (RSI) oscillator.
*   **Risk Management:** Robust risk guardrails tracking stop-loss, take-profit, portfolio drawdown, and risk per trade limits.
*   **Backtesting Engine:** Evaluate strategies against historical mock data to compare Win Rate, Max Drawdown, and Sharpe Ratios.
*   **Dual Persistence:** Seamlessly switches between MySQL database storage and a local file-based fallback if the database is unavailable.
*   **Premium Frontend:** Glassmorphic UI with real-time DOM updates, dynamic charts, and flash animations for market ticks.

## 🛠️ Technology Stack

*   **Backend:** Pure Java 8+
    *   No external heavy frameworks (uses built-in `com.sun.net.httpserver.HttpServer`).
    *   **Design Patterns:** Observer (Event-driven market data), Strategy (Trading algorithms), Singleton (Database connections).
*   **Database:** MySQL with JDBC (`mysql-connector-j-9.7.0.jar`).
*   **Frontend:** HTML5, Vanilla CSS3 (Dark Theme + Glassmorphism), Vanilla JavaScript.
    *   Communication: RESTful APIs and SSE (`EventSource`) for live streaming.

## 📦 Setup & Installation

### Prerequisites
*   Java Development Kit (JDK) 8 or higher
*   MySQL Server (Optional - system falls back to file storage if missing)

### 1. Database Setup (Optional)
If you want to use MySQL instead of the file fallback, ensure your MySQL server is running and update the credentials in `backend/DBManager.java`:
```java
private static final String DB_USER = "root";
private static final String DB_PASS = "your_password";
```
*Note: The application will automatically create the `forex` database and required tables upon starting.*

### 2. Compile the Project
Navigate to the `backend` directory and compile the Java source files. The MySQL connector JAR is included.
```bash
cd backend
javac -cp "mysql-connector-j-9.7.0.jar;." *.java
```

### 3. Run the Server
Start the backend server:
```bash
java -cp "mysql-connector-j-9.7.0.jar;." MainServer
```

### 4. Access the Platform
Once the server is running, open your web browser and navigate to:
```
http://localhost:8080
```

## 📂 Project Structure

```text
Arthyantra/
├── backend/
│   ├── MainServer.java         # Core HTTP server and API router
│   ├── DBManager.java          # MySQL database connection & fallback handling
│   ├── FileManager.java        # Local storage fallback handler
│   ├── Portfolio.java          # Service layer for Trade CRUD operations
│   ├── Trade.java              # Trade Entity model
│   ├── Signal.java             # Trading signal model
│   ├── MarketSimulator.java    # Subject for the Observer pattern
│   ├── MarketObserver.java     # Interface for the Observer pattern
│   ├── TradeEngine.java        # Auto-trade execution observer
│   ├── RiskManager.java        # Risk and drawdown management observer
│   ├── SignalLogger.java       # Auditing observer
│   ├── TradingStrategy.java    # Interface for the Strategy pattern
│   ├── MovingAverageStrategy.java
│   ├── RSIStrategy.java
│   ├── BacktestEngine.java     # Historical strategy tester
│   └── mysql-connector-j...jar # JDBC driver
└── frontend/
    ├── index.html              # Main UI structure
    ├── style.css               # Styling, animations, design system
    └── script.js               # API integrations, SSE listener, DOM manipulation
```

## 🎓 Academic Submission
This project was developed as a comprehensive college project demonstrating the practical application of object-oriented design patterns, real-time data streaming, and full-stack integration without relying on excessive abstractions or heavy frameworks. See `StudyGuide.md` for an in-depth architectural breakdown.
