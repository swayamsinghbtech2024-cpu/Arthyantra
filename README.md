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
*   **Native Desktop App:** A high-fidelity JavaFX application that runs alongside the web server, sharing the same live data and trading engine in a single memory space.

## 🛠️ Technology Stack

*   **Backend:** Pure Java 21+
    *   No external heavy frameworks (uses built-in `com.sun.net.httpserver.HttpServer`).
    *   **Design Patterns:** Observer (Event-driven market data), Strategy (Trading algorithms), Singleton (Database connections), Modular View Pattern (JavaFX).
*   **Database:** MySQL with JDBC (`mysql-connector-j-9.7.0.jar`).
*   **Web Frontend:** HTML5, Vanilla CSS3 (Dark Theme + Glassmorphism), Vanilla JavaScript.
*   **Desktop Frontend:** JavaFX 21 (Native UI with CSS Skinning).

## 📦 Setup & Installation

### Prerequisites
*   Java Development Kit (JDK) 21 or higher
*   MySQL Server (Optional - system falls back to file storage if missing)

### 1. Database Setup (Optional)
If you want to use MySQL instead of the file fallback, ensure your MySQL server is running and update the credentials in `backend/DBManager.java`:
```java
private static final String DB_USER = "root";
private static final String DB_PASS = "your_password";
```
*Note: The application will automatically create the `forex` database and required tables upon starting.*

### 2. Compile & Run (Web Platform)
Navigate to the `backend` directory:
```bash
cd backend
javac -cp "mysql-connector-j-9.7.0.jar;." *.java
java -cp "mysql-connector-j-9.7.0.jar;." MainServer
```
Access at `http://localhost:8080`.

### 3. Run (Desktop Platform)
The Desktop app includes its own server, so you don't need to run `MainServer` separately if using the desktop app.
Navigate to the `javafx_app` directory and use the provided utility:
```bash
cd javafx_app
.\run.bat
```

## 📂 Project Structure

```text
Arthyantra/
├── backend/
│   ├── MainServer.java         # Core HTTP server and API router
│   ├── ...                     # Portfolio, Trade, Engines, Strategies (Shared logic)
│   └── mysql-connector-j...jar # JDBC driver
├── frontend/                   # Web browser interface (HTML/CSS/JS)
├── javafx_app/                 # Native Desktop App (JavaFX 21)
│   ├── MainApp.java            # Entry point for Desktop + Integrated Server
│   ├── DashboardView.java      # Real-time dashboard (MarketObserver)
│   ├── StrategiesView.java     # Strategy config & Signal log
│   ├── BacktestView.java       # Historical simulator interface
│   ├── RiskView.java           # Risk guardrail management
│   ├── theme.css               # Native CSS skin (Terminal Dark Aesthetic)
│   ├── run.bat                 # Automation script for Win (requires JDK 21)
│   └── javafx-sdk-21/          # Bundled JavaFX libraries
└── data/                       # Local trade & signal storage
```

## 🎓 Academic Submission
This project was developed as a comprehensive college project demonstrating the practical application of object-oriented design patterns, real-time data streaming, and full-stack integration without relying on excessive abstractions or heavy frameworks. See `StudyGuide.md` for an in-depth architectural breakdown.
