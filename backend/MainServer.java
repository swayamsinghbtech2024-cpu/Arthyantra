import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * MainServer - Controller Layer (HTTP Server)
 * 
 * Lightweight HTTP server using Java's built-in HttpServer.
 * Handles all API endpoints and serves the frontend static files.
 * Implements CORS support for cross-origin requests.
 * 
 * Architecture:
 * - MarketSimulator (Subject) generates price ticks
 * - TradeEngine, RiskManager, SignalLogger (Observers) react to market data
 * - TradingStrategy (Strategy Pattern) can be dynamically switched
 * 
 * Endpoints:
 *   POST /add           - Add a new trade
 *   GET  /portfolio     - View all trades + summary
 *   POST /update        - Update trade quantity/price
 *   POST /delete        - Delete a trade by ID
 *   GET  /strategy      - Run strategy simulations (legacy)
 *   GET  /status         - Server health check
 *   GET  /stream         - SSE stream of real-time market data
 *   POST /simulation/start  - Start market simulation
 *   POST /simulation/stop   - Stop market simulation
 *   POST /strategy/config   - Switch active strategy
 *   POST /backtest          - Run backtesting
 *   GET  /risk              - Get risk metrics
 *   POST /risk/config       - Update risk parameters
 *   GET  /signals           - Get signal log
 *   GET  /market            - Get current market prices
 *   GET  /*                 - Serve frontend static files
 */
public class MainServer {

    // ─── Configuration ───────────────────────────────────────────────
    private static final int PORT = 8080;
    private static final String FRONTEND_DIR = "../frontend";

    // ─── Core Components ─────────────────────────────────────────────
    private static Portfolio portfolio = new Portfolio();
    private static MarketSimulator marketSimulator = new MarketSimulator();
    private static TradeEngine tradeEngine;
    private static RiskManager riskManager;
    private static SignalLogger signalLogger;

    // SSE clients
    private static final List<HttpExchange> sseClients = new CopyOnWriteArrayList<>();

    // ─── Main Entry Point ────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║   Arthayantra — Forex Portfolio & Strategy Simulator     ║");
        System.out.println("║   Starting server...                                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        // Step 1: Initialize file storage
        FileManager.initialize();
        System.out.println("[Server] File storage initialized.");

        // Step 2: Initialize database (non-blocking)
        try {
            DBManager.initializeSchema();
            System.out.println("[Server] Database initialized successfully.");
        } catch (Exception e) {
            System.out.println("[Server] ⚠ Database unavailable: " + e.getMessage());
            System.out.println("[Server] → System will use file storage as fallback.");
        }

        // Step 3: Load existing trades
        portfolio.loadTrades();
        System.out.println("[Server] Portfolio loaded with " + portfolio.getTradeCount() + " trades.");

        // Step 4: Initialize Observer Pattern components
        tradeEngine = new TradeEngine(portfolio);
        riskManager = new RiskManager(portfolio);
        signalLogger = new SignalLogger();

        // Register observers with the market simulator
        marketSimulator.addObserver(tradeEngine);
        marketSimulator.addObserver(riskManager);
        marketSimulator.addObserver(signalLogger);

        // Add SSE broadcaster as an observer
        marketSimulator.addObserver(new MarketObserver() {
            @Override
            public void onPriceUpdate(String instrument, double price, String timestamp) {
                broadcastSSE("price", marketSimulator.toJSON());
            }
            @Override
            public void onSignalGenerated(Signal signal) {
                broadcastSSE("signal", signal.toJSON());
            }
        });

        System.out.println("[Server] Observer Pattern initialized: TradeEngine, RiskManager, SignalLogger, SSE");

        // Step 5: Create HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // ─── Register API endpoints ──────────────────────────────────
        // Original endpoints
        server.createContext("/add", MainServer::handleAdd);
        server.createContext("/portfolio", MainServer::handlePortfolio);
        server.createContext("/update", MainServer::handleUpdate);
        server.createContext("/delete", MainServer::handleDelete);
        server.createContext("/strategy", MainServer::handleStrategy);
        server.createContext("/status", MainServer::handleStatus);

        // New endpoints
        server.createContext("/stream", MainServer::handleSSE);
        server.createContext("/simulation/start", MainServer::handleSimulationStart);
        server.createContext("/simulation/stop", MainServer::handleSimulationStop);
        server.createContext("/strategy/config", MainServer::handleStrategyConfig);
        server.createContext("/backtest", MainServer::handleBacktest);
        server.createContext("/risk", MainServer::handleRisk);
        server.createContext("/risk/config", MainServer::handleRiskConfig);
        server.createContext("/signals", MainServer::handleSignals);
        server.createContext("/market", MainServer::handleMarket);

        // Serve frontend static files (must be last)
        server.createContext("/", MainServer::handleStaticFiles);

        // Start the server with a thread pool
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println();
        System.out.println("✅ Server is running at: http://localhost:" + PORT);
        System.out.println("   Open this URL in your browser to access the application.");
        System.out.println("   Press Ctrl+C to stop the server.");
        System.out.println();
    }

    // ═══════════════════════════════════════════════════════════════════
    // ORIGINAL API HANDLERS (preserved)
    // ═══════════════════════════════════════════════════════════════════

    // ─── POST /add ───────────────────────────────────────────────────
    private static void handleAdd(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJSON(exchange, 405, "{\"error\":\"Method not allowed. Use POST.\"}"); return;
        }
        try {
            String body = readRequestBody(exchange);
            System.out.println("[/add] Received: " + body);

            String type = extractJSONValue(body, "type");
            String instrument = extractJSONValue(body, "instrument");
            double quantity = Double.parseDouble(extractJSONValue(body, "quantity"));
            double price = Double.parseDouble(extractJSONValue(body, "price"));

            String validationError = Trade.validate(type, instrument, quantity, price);
            if (validationError != null) {
                sendJSON(exchange, 400, "{\"error\":\"" + validationError + "\"}"); return;
            }

            Trade newTrade = new Trade(type, instrument, quantity, price);

            // Parse optional risk fields
            String slVal = extractJSONValue(body, "stopLoss");
            String tpVal = extractJSONValue(body, "takeProfit");
            if (slVal != null) {
                try { newTrade.setStopLoss(Double.parseDouble(slVal)); } catch (Exception ignored) {}
            }
            if (tpVal != null) {
                try { newTrade.setTakeProfit(Double.parseDouble(tpVal)); } catch (Exception ignored) {}
            }

            Trade added = portfolio.addTrade(newTrade);

            String response = "{\"success\":true,\"message\":\"Trade added successfully\"," +
                              "\"trade\":" + added.toJSON() + "}";
            sendJSON(exchange, 200, response);
        } catch (NumberFormatException e) {
            sendJSON(exchange, 400, "{\"error\":\"Invalid number format for quantity or price.\"}");
        } catch (Exception e) {
            System.out.println("[/add] Error: " + e.getMessage());
            sendJSON(exchange, 500, "{\"error\":\"Server error: " + e.getMessage() + "\"}");
        }
    }

    // ─── GET /portfolio ──────────────────────────────────────────────
    private static void handlePortfolio(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }
        try {
            List<Trade> trades = portfolio.getTrades();
            StringBuilder tradesJSON = new StringBuilder("[");
            for (int i = 0; i < trades.size(); i++) {
                if (i > 0) tradesJSON.append(",");
                tradesJSON.append(trades.get(i).toJSON());
            }
            tradesJSON.append("]");

            // Build enhanced summary with win/loss ratio
            int buyCount = 0, sellCount = 0;
            for (Trade t : trades) {
                if ("BUY".equals(t.getType())) buyCount++;
                else sellCount++;
            }

            String response = "{" +
                    "\"trades\":" + tradesJSON.toString() + "," +
                    "\"summary\":" + portfolio.toSummaryJSON() + "," +
                    "\"buyCount\":" + buyCount + "," +
                    "\"sellCount\":" + sellCount +
                    "}";
            sendJSON(exchange, 200, response);
        } catch (Exception e) {
            System.out.println("[/portfolio] Error: " + e.getMessage());
            sendJSON(exchange, 500, "{\"error\":\"Failed to load portfolio.\"}");
        }
    }

    // ─── POST /update ────────────────────────────────────────────────
    private static void handleUpdate(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJSON(exchange, 405, "{\"error\":\"Method not allowed. Use POST.\"}"); return;
        }
        try {
            String body = readRequestBody(exchange);
            System.out.println("[/update] Received: " + body);
            int id = Integer.parseInt(extractJSONValue(body, "id"));
            double quantity = Double.parseDouble(extractJSONValue(body, "quantity"));
            double price = Double.parseDouble(extractJSONValue(body, "price"));
            if (quantity <= 0 || price <= 0) {
                sendJSON(exchange, 400, "{\"error\":\"Quantity and price must be greater than zero.\"}"); return;
            }
            boolean success = portfolio.updateTrade(id, quantity, price);
            if (success) {
                Trade updated = portfolio.getTradeById(id);
                String response = "{\"success\":true,\"message\":\"Trade updated successfully\"," +
                                  "\"trade\":" + (updated != null ? updated.toJSON() : "null") + "}";
                sendJSON(exchange, 200, response);
            } else {
                sendJSON(exchange, 404, "{\"error\":\"Trade not found with ID: " + id + "\"}");
            }
        } catch (NumberFormatException e) {
            sendJSON(exchange, 400, "{\"error\":\"Invalid number format.\"}");
        } catch (Exception e) {
            System.out.println("[/update] Error: " + e.getMessage());
            sendJSON(exchange, 500, "{\"error\":\"Server error: " + e.getMessage() + "\"}");
        }
    }

    // ─── POST /delete ────────────────────────────────────────────────
    private static void handleDelete(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJSON(exchange, 405, "{\"error\":\"Method not allowed. Use POST.\"}"); return;
        }
        try {
            String body = readRequestBody(exchange);
            System.out.println("[/delete] Received: " + body);
            int id = Integer.parseInt(extractJSONValue(body, "id"));
            boolean success = portfolio.deleteTrade(id);
            if (success) {
                sendJSON(exchange, 200,
                        "{\"success\":true,\"message\":\"Trade " + id + " deleted successfully.\"}");
            } else {
                sendJSON(exchange, 404, "{\"error\":\"Trade not found with ID: " + id + "\"}");
            }
        } catch (NumberFormatException e) {
            sendJSON(exchange, 400, "{\"error\":\"Invalid trade ID format.\"}");
        } catch (Exception e) {
            System.out.println("[/delete] Error: " + e.getMessage());
            sendJSON(exchange, 500, "{\"error\":\"Server error: " + e.getMessage() + "\"}");
        }
    }

    // ─── GET /strategy (legacy) ──────────────────────────────────────
    private static void handleStrategy(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }
        try {
            List<Trade> trades = portfolio.getTrades();
            if (trades.isEmpty()) {
                sendJSON(exchange, 200,
                        "{\"buyAndHold\":0,\"randomStrategy\":0,\"momentumStrategy\":0," +
                        "\"bestStrategy\":\"N/A\",\"bestProfit\":0,\"tradeCount\":0," +
                        "\"message\":\"No trades in portfolio. Add some trades first.\"}");
                return;
            }
            String result = StrategySimulator.compareStrategies(trades);
            sendJSON(exchange, 200, result);
        } catch (Exception e) {
            System.out.println("[/strategy] Error: " + e.getMessage());
            sendJSON(exchange, 500, "{\"error\":\"Strategy simulation failed.\"}");
        }
    }

    // ─── GET /status ─────────────────────────────────────────────────
    private static void handleStatus(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }
        boolean dbConnected = DBManager.testConnection();
        boolean fileReady = FileManager.fileExists();
        String response = "{" +
                "\"status\":\"running\"," +
                "\"port\":" + PORT + "," +
                "\"database\":\"" + (dbConnected ? "connected" : "disconnected") + "\"," +
                "\"fileStorage\":\"" + (fileReady ? "available" : "empty") + "\"," +
                "\"tradeCount\":" + portfolio.getTradeCount() + "," +
                "\"simulationRunning\":" + marketSimulator.isRunning() + "," +
                "\"activeStrategy\":\"" + marketSimulator.getActiveStrategy().getName() + "\"" +
                "}";
        sendJSON(exchange, 200, response);
    }

    // ═══════════════════════════════════════════════════════════════════
    // NEW API HANDLERS
    // ═══════════════════════════════════════════════════════════════════

    // ─── GET /stream (Server-Sent Events) ────────────────────────────
    /**
     * SSE endpoint for real-time market data streaming.
     * The connection stays open and the server pushes data events.
     */
    private static void handleSSE(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }

        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/event-stream");
        headers.set("Cache-Control", "no-cache");
        headers.set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        sseClients.add(exchange);
        System.out.println("[SSE] Client connected. Total clients: " + sseClients.size());

        // Send initial data
        try {
            OutputStream os = exchange.getResponseBody();
            String initData = "data: " + marketSimulator.toJSON() + "\n\n";
            os.write(initData.getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (Exception e) {
            sseClients.remove(exchange);
        }
    }

    /**
     * Broadcasts an SSE event to all connected clients.
     */
    private static void broadcastSSE(String eventType, String data) {
        String message = "event: " + eventType + "\ndata: " + data + "\n\n";
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);

        Iterator<HttpExchange> it = sseClients.iterator();
        while (it.hasNext()) {
            HttpExchange client = it.next();
            try {
                OutputStream os = client.getResponseBody();
                os.write(bytes);
                os.flush();
            } catch (Exception e) {
                sseClients.remove(client);
            }
        }
    }

    // ─── POST /simulation/start ──────────────────────────────────────
    private static void handleSimulationStart(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }
        marketSimulator.start();
        sendJSON(exchange, 200, "{\"success\":true,\"message\":\"Simulation started\"," +
                "\"strategy\":\"" + marketSimulator.getActiveStrategy().getName() + "\"}");
    }

    // ─── POST /simulation/stop ───────────────────────────────────────
    private static void handleSimulationStop(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }
        marketSimulator.stop();
        sendJSON(exchange, 200, "{\"success\":true,\"message\":\"Simulation stopped\"}");
    }

    // ─── POST /strategy/config ───────────────────────────────────────
    /**
     * Switch the active trading strategy.
     * Body: { "strategy": "MA" | "RSI" }
     * Optional: { "autoTrade": true/false, "confidence": 0.6 }
     */
    private static void handleStrategyConfig(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }
        try {
            String body = readRequestBody(exchange);
            String strategyName = extractJSONValue(body, "strategy");

            if (strategyName != null) {
                switch (strategyName.toUpperCase()) {
                    case "MA":
                    case "MOVING_AVERAGE":
                        marketSimulator.setStrategy(new MovingAverageStrategy());
                        break;
                    case "RSI":
                        marketSimulator.setStrategy(new RSIStrategy());
                        break;
                    default:
                        sendJSON(exchange, 400, "{\"error\":\"Unknown strategy: " + strategyName + ". Use MA or RSI.\"}");
                        return;
                }
            }

            // Configure auto-trade
            String autoTrade = extractJSONValue(body, "autoTrade");
            if (autoTrade != null) {
                tradeEngine.setAutoTradeEnabled(Boolean.parseBoolean(autoTrade));
            }

            String confidence = extractJSONValue(body, "confidence");
            if (confidence != null) {
                tradeEngine.setConfidenceThreshold(Double.parseDouble(confidence));
            }

            sendJSON(exchange, 200, "{\"success\":true," +
                    "\"activeStrategy\":\"" + marketSimulator.getActiveStrategy().getName() + "\"," +
                    "\"tradeEngine\":" + tradeEngine.toJSON() + "}");
        } catch (Exception e) {
            sendJSON(exchange, 500, "{\"error\":\"Strategy config failed: " + e.getMessage() + "\"}");
        }
    }

    // ─── POST /backtest ──────────────────────────────────────────────
    /**
     * Run backtesting against historical data.
     * Body: { "instrument": "USD/INR", "dataPoints": 500 }
     */
    private static void handleBacktest(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }
        try {
            String body = readRequestBody(exchange);
            String instrument = extractJSONValue(body, "instrument");
            String dpStr = extractJSONValue(body, "dataPoints");

            if (instrument == null || instrument.isEmpty()) instrument = "USD/INR";
            int dataPoints = 500;
            if (dpStr != null) {
                dataPoints = Integer.parseInt(dpStr);
                dataPoints = Math.min(2000, Math.max(100, dataPoints));
            }

            String result = BacktestEngine.compareStrategies(instrument, dataPoints);
            sendJSON(exchange, 200, result);
        } catch (Exception e) {
            System.out.println("[/backtest] Error: " + e.getMessage());
            sendJSON(exchange, 500, "{\"error\":\"Backtesting failed: " + e.getMessage() + "\"}");
        }
    }

    // ─── GET /risk ───────────────────────────────────────────────────
    private static void handleRisk(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            handleRiskConfig(exchange);
            return;
        }

        sendJSON(exchange, 200, riskManager.toJSON());
    }

    // ─── POST /risk/config ───────────────────────────────────────────
    /**
     * Update risk management parameters.
     * Body: { "stopLoss": 5, "takeProfit": 10, "maxDrawdown": 20, "riskPerTrade": 2 }
     */
    private static void handleRiskConfig(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }
        try {
            String body = readRequestBody(exchange);

            String sl = extractJSONValue(body, "stopLoss");
            String tp = extractJSONValue(body, "takeProfit");
            String md = extractJSONValue(body, "maxDrawdown");
            String rpt = extractJSONValue(body, "riskPerTrade");

            if (sl != null) riskManager.setStopLossPercent(Double.parseDouble(sl));
            if (tp != null) riskManager.setTakeProfitPercent(Double.parseDouble(tp));
            if (md != null) riskManager.setMaxDrawdownPercent(Double.parseDouble(md));
            if (rpt != null) riskManager.setRiskPerTradePercent(Double.parseDouble(rpt));

            sendJSON(exchange, 200, "{\"success\":true,\"risk\":" + riskManager.toJSON() + "}");
        } catch (Exception e) {
            sendJSON(exchange, 500, "{\"error\":\"Risk config failed: " + e.getMessage() + "\"}");
        }
    }

    // ─── GET /signals ────────────────────────────────────────────────
    private static void handleSignals(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }
        String response = "{\"signals\":" + tradeEngine.signalLogToJSON() + "," +
                "\"tradeEngine\":" + tradeEngine.toJSON() + "}";
        sendJSON(exchange, 200, response);
    }

    // ─── GET /market ─────────────────────────────────────────────────
    private static void handleMarket(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }
        sendJSON(exchange, 200, marketSimulator.toJSON());
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATIC FILE SERVER
    // ═══════════════════════════════════════════════════════════════════

    private static void handleStaticFiles(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/".equals(path) || path.isEmpty()) {
            path = "/index.html";
        }
        File file = new File(FRONTEND_DIR + path);
        if (!file.exists() || file.isDirectory()) {
            String notFound = "<!DOCTYPE html><html><body><h1>404 Not Found</h1></body></html>";
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(404, notFound.getBytes().length);
            exchange.getResponseBody().write(notFound.getBytes());
            exchange.close();
            return;
        }
        String contentType = getContentType(path);
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, fileBytes.length);
        exchange.getResponseBody().write(fileBytes);
        exchange.close();
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            return body.toString();
        }
    }

    private static void sendJSON(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private static void setCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static String extractJSONValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;
        int colonIndex = json.indexOf(":", keyIndex + searchKey.length());
        if (colonIndex == -1) return null;
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') valueStart++;
        if (valueStart >= json.length()) return null;
        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd == -1) return null;
            return json.substring(valueStart + 1, valueEnd);
        }
        int valueEnd = valueStart;
        while (valueEnd < json.length() &&
               json.charAt(valueEnd) != ',' &&
               json.charAt(valueEnd) != '}' &&
               json.charAt(valueEnd) != ' ') {
            valueEnd++;
        }
        return json.substring(valueStart, valueEnd).trim();
    }

    private static String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".ico")) return "image/x-icon";
        return "text/plain";
    }
}