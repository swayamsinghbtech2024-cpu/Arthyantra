import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * MainServer - Controller Layer (HTTP Server)
 * 
 * Lightweight HTTP server using Java's built-in HttpServer.
 * Handles all API endpoints and serves the frontend static files.
 * Implements CORS support for cross-origin requests.
 * 
 * Endpoints:
 *   POST /add        - Add a new trade
 *   GET  /portfolio   - View all trades + summary
 *   POST /update      - Update trade quantity/price
 *   POST /delete      - Delete a trade by ID
 *   GET  /strategy    - Run strategy simulations
 *   GET  /status      - Server health check
 *   GET  /*           - Serve frontend static files
 */
public class MainServer {

    // ─── Configuration ───────────────────────────────────────────────────
    private static final int PORT = 8080;
    private static final String FRONTEND_DIR = "../frontend";

    // Portfolio instance (business logic layer)
    private static Portfolio portfolio = new Portfolio();

    // ─── Main Entry Point ────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║   Forex Portfolio Management & Strategy Simulator       ║");
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

        // Step 4: Create HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Register API endpoints
        server.createContext("/add", MainServer::handleAdd);
        server.createContext("/portfolio", MainServer::handlePortfolio);
        server.createContext("/update", MainServer::handleUpdate);
        server.createContext("/delete", MainServer::handleDelete);
        server.createContext("/strategy", MainServer::handleStrategy);
        server.createContext("/status", MainServer::handleStatus);

        // Serve frontend static files (must be last - catches all other paths)
        server.createContext("/", MainServer::handleStaticFiles);

        // Start the server
        server.setExecutor(null); // Use default executor
        server.start();

        System.out.println();
        System.out.println("✅ Server is running at: http://localhost:" + PORT);
        System.out.println("   Open this URL in your browser to access the application.");
        System.out.println("   Press Ctrl+C to stop the server.");
        System.out.println();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // API HANDLERS
    // ═══════════════════════════════════════════════════════════════════════

    // ─── POST /add ───────────────────────────────────────────────────────
    /**
     * Adds a new trade to the portfolio.
     * Expects JSON body: { "type", "instrument", "quantity", "price" }
     */
    private static void handleAdd(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJSON(exchange, 405, "{\"error\":\"Method not allowed. Use POST.\"}");
            return;
        }

        try {
            // Read request body
            String body = readRequestBody(exchange);
            System.out.println("[/add] Received: " + body);

            // Parse JSON manually
            String type = extractJSONValue(body, "type");
            String instrument = extractJSONValue(body, "instrument");
            double quantity = Double.parseDouble(extractJSONValue(body, "quantity"));
            double price = Double.parseDouble(extractJSONValue(body, "price"));

            // Validate input
            String validationError = Trade.validate(type, instrument, quantity, price);
            if (validationError != null) {
                sendJSON(exchange, 400, "{\"error\":\"" + validationError + "\"}");
                return;
            }

            // Create and add trade
            Trade newTrade = new Trade(type, instrument, quantity, price);
            Trade added = portfolio.addTrade(newTrade);

            // Send success response
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

    // ─── GET /portfolio ──────────────────────────────────────────────────
    /**
     * Returns all trades and portfolio summary as JSON.
     */
    private static void handlePortfolio(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            List<Trade> trades = portfolio.getTrades();

            // Build trades JSON array
            StringBuilder tradesJSON = new StringBuilder("[");
            for (int i = 0; i < trades.size(); i++) {
                if (i > 0) tradesJSON.append(",");
                tradesJSON.append(trades.get(i).toJSON());
            }
            tradesJSON.append("]");

            // Build complete response
            String response = "{" +
                    "\"trades\":" + tradesJSON.toString() + "," +
                    "\"summary\":" + portfolio.toSummaryJSON() +
                    "}";

            sendJSON(exchange, 200, response);

        } catch (Exception e) {
            System.out.println("[/portfolio] Error: " + e.getMessage());
            sendJSON(exchange, 500, "{\"error\":\"Failed to load portfolio.\"}");
        }
    }

    // ─── POST /update ────────────────────────────────────────────────────
    /**
     * Updates an existing trade's quantity and price.
     * Expects JSON body: { "id", "quantity", "price" }
     */
    private static void handleUpdate(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJSON(exchange, 405, "{\"error\":\"Method not allowed. Use POST.\"}");
            return;
        }

        try {
            String body = readRequestBody(exchange);
            System.out.println("[/update] Received: " + body);

            int id = Integer.parseInt(extractJSONValue(body, "id"));
            double quantity = Double.parseDouble(extractJSONValue(body, "quantity"));
            double price = Double.parseDouble(extractJSONValue(body, "price"));

            // Validate
            if (quantity <= 0 || price <= 0) {
                sendJSON(exchange, 400, "{\"error\":\"Quantity and price must be greater than zero.\"}");
                return;
            }

            // Update trade
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

    // ─── POST /delete ────────────────────────────────────────────────────
    /**
     * Deletes a trade by its ID.
     * Expects JSON body: { "id": <number> }
     */
    private static void handleDelete(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJSON(exchange, 405, "{\"error\":\"Method not allowed. Use POST.\"}");
            return;
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

    // ─── GET /strategy ───────────────────────────────────────────────────
    /**
     * Runs all strategy simulations and returns comparison results.
     */
    private static void handleStrategy(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
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

    // ─── GET /status ─────────────────────────────────────────────────────
    /**
     * Health check endpoint. Returns server status and DB connectivity.
     */
    private static void handleStatus(HttpExchange exchange) throws IOException {
        setCorsHeaders(exchange);

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        boolean dbConnected = DBManager.testConnection();
        boolean fileReady = FileManager.fileExists();

        String response = "{" +
                "\"status\":\"running\"," +
                "\"port\":" + PORT + "," +
                "\"database\":\"" + (dbConnected ? "connected" : "disconnected") + "\"," +
                "\"fileStorage\":\"" + (fileReady ? "available" : "empty") + "\"," +
                "\"tradeCount\":" + portfolio.getTradeCount() +
                "}";

        sendJSON(exchange, 200, response);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STATIC FILE SERVER
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Serves frontend static files (HTML, CSS, JS).
     * Maps "/" to "/index.html" automatically.
     */
    private static void handleStaticFiles(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // Default to index.html for root path
        if ("/".equals(path) || path.isEmpty()) {
            path = "/index.html";
        }

        // Resolve file path
        File file = new File(FRONTEND_DIR + path);

        if (!file.exists() || file.isDirectory()) {
            String notFound = "<!DOCTYPE html><html><body><h1>404 Not Found</h1></body></html>";
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(404, notFound.getBytes().length);
            exchange.getResponseBody().write(notFound.getBytes());
            exchange.close();
            return;
        }

        // Determine content type
        String contentType = getContentType(path);
        byte[] fileBytes = Files.readAllBytes(file.toPath());

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, fileBytes.length);
        exchange.getResponseBody().write(fileBytes);
        exchange.close();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Reads the entire request body as a string.
     */
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

    /**
     * Sends a JSON response with the given status code.
     */
    private static void sendJSON(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    /**
     * Sets CORS headers to allow cross-origin requests from the frontend.
     */
    private static void setCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    /**
     * Extracts a value from a simple JSON string by key.
     * Handles both string and numeric values.
     * This is a lightweight JSON parser for simple flat objects.
     */
    private static String extractJSONValue(String json, String key) {
        // Look for "key": "value" or "key": number
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);

        if (keyIndex == -1) {
            return null;
        }

        // Find the colon after the key
        int colonIndex = json.indexOf(":", keyIndex + searchKey.length());
        if (colonIndex == -1) {
            return null;
        }

        // Skip whitespace after colon
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') {
            valueStart++;
        }

        if (valueStart >= json.length()) {
            return null;
        }

        // Check if value is a string (starts with quote)
        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd == -1) return null;
            return json.substring(valueStart + 1, valueEnd);
        }

        // Value is a number or boolean
        int valueEnd = valueStart;
        while (valueEnd < json.length() &&
               json.charAt(valueEnd) != ',' &&
               json.charAt(valueEnd) != '}' &&
               json.charAt(valueEnd) != ' ') {
            valueEnd++;
        }

        return json.substring(valueStart, valueEnd).trim();
    }

    /**
     * Determines the MIME content type based on file extension.
     */
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