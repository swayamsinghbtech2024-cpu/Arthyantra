import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardView extends VBox implements MarketObserver {

    private Label lblTotalTrades;
    private Label lblTotalInvested;
    private Label lblTotalRevenue;
    private Label lblProfitLoss;

    private Map<String, Label> priceLabels = new HashMap<>();
    private Map<String, Label> changeLabels = new HashMap<>();
    private Map<String, Double> previousPrices = new HashMap<>();

    private TableView<Trade> tradeTable;

    public DashboardView() {
        setSpacing(20);

        // --- Stats Row ---
        lblTotalTrades = new Label("0");
        lblTotalInvested = new Label("₹0.00");
        lblTotalRevenue = new Label("₹0.00");
        lblProfitLoss = new Label("₹0.00");

        HBox statsRow = new HBox(15);
        statsRow.getChildren().addAll(
            createStatCard("TOTAL TRADES", lblTotalTrades),
            createStatCard("TOTAL INVESTED", lblTotalInvested),
            createStatCard("TOTAL REVENUE", lblTotalRevenue),
            createStatCard("PROFIT/LOSS", lblProfitLoss)
        );

        // --- Live Market Prices ---
        Label marketTitle = new Label("Live Market Prices");
        marketTitle.getStyleClass().add("header-label");

        HBox marketGrid = new HBox(15);
        String[] instruments = {"USD/INR", "EUR/USD", "GBP/USD", "USD/JPY", "GOLD", "NIFTY 50"};
        for (String inst : instruments) {
            marketGrid.getChildren().add(createMarketCard(inst));
            previousPrices.put(inst, 0.0);
        }

        // --- Bottom Area (Trade Execution & History) ---
        HBox bottomArea = new HBox(20);
        
        VBox execTrade = createTradeForm();
        
        VBox historyBox = createTradeHistory();
        HBox.setHgrow(historyBox, Priority.ALWAYS);

        bottomArea.getChildren().addAll(execTrade, historyBox);

        getChildren().addAll(statsRow, marketTitle, marketGrid, bottomArea);

        // Connect to Backend
        MainServer.getMarketSimulator().addObserver(this);
        refreshPortfolioStats();
        refreshTradeTable();
    }

    private VBox createStatCard(String title, Label valueLabel) {
        VBox card = new VBox(5);
        card.getStyleClass().add("glass-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        
        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("subtitle-label");
        
        valueLabel.getStyleClass().add("title-label");
        
        card.getChildren().addAll(lblTitle, valueLabel);
        return card;
    }

    private VBox createMarketCard(String instrument) {
        VBox card = new VBox(8);
        card.getStyleClass().add("market-card");
        HBox.setHgrow(card, Priority.ALWAYS);

        Label lblInst = new Label(instrument);
        lblInst.getStyleClass().add("market-name");

        Label lblPrice = new Label("0.0000");
        lblPrice.getStyleClass().add("market-price");
        priceLabels.put(instrument, lblPrice);

        Label lblChange = new Label("0.00%");
        lblChange.getStyleClass().add("subtitle-label");
        changeLabels.put(instrument, lblChange);

        card.getChildren().addAll(lblInst, lblPrice, lblChange);
        return card;
    }

    private VBox createTradeForm() {
        VBox execTrade = new VBox(15);
        execTrade.getStyleClass().add("glass-card");
        execTrade.setPrefWidth(300);
        Label execTitle = new Label("Execute Trade");
        execTitle.getStyleClass().add("header-label");
        
        ComboBox<String> cmbInstrument = new ComboBox<>();
        cmbInstrument.getItems().addAll("USD/INR", "EUR/USD", "GBP/USD", "USD/JPY", "GOLD", "NIFTY 50");
        cmbInstrument.setValue("USD/INR");
        cmbInstrument.setMaxWidth(Double.MAX_VALUE);
        
        TextField txtQuantity = new TextField();
        txtQuantity.setPromptText("Quantity");

        TextField txtPrice = new TextField();
        txtPrice.setPromptText("Price");

        HBox btnBox = new HBox(10);
        Button btnBuy = new Button("BUY");
        btnBuy.getStyleClass().addAll("button-primary");
        btnBuy.setStyle("-fx-background-color: #10B981;");
        
        Button btnSell = new Button("SELL");
        btnSell.getStyleClass().addAll("button-primary");
        btnSell.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white;");
        
        btnBox.getChildren().addAll(btnBuy, btnSell);

        btnBuy.setOnAction(e -> submitTrade("BUY", cmbInstrument.getValue(), txtQuantity.getText(), txtPrice.getText()));
        btnSell.setOnAction(e -> submitTrade("SELL", cmbInstrument.getValue(), txtQuantity.getText(), txtPrice.getText()));

        execTrade.getChildren().addAll(execTitle, new Label("Instrument"), cmbInstrument, new Label("Quantity"), txtQuantity, new Label("Price"), txtPrice, btnBox);
        return execTrade;
    }

    private VBox createTradeHistory() {
        VBox tradeBox = new VBox(15);
        tradeBox.getStyleClass().add("glass-card");
        
        Label histTitle = new Label("Trade History");
        histTitle.getStyleClass().add("header-label");

        tradeTable = new TableView<>();
        tradeTable.getStyleClass().add("table-view");

        TableColumn<Trade, Integer> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<Trade, String> colType = new TableColumn<>("TYPE");
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));

        TableColumn<Trade, String> colInst = new TableColumn<>("INSTRUMENT");
        colInst.setCellValueFactory(new PropertyValueFactory<>("instrument"));

        TableColumn<Trade, Double> colQty = new TableColumn<>("QUANTITY");
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        TableColumn<Trade, Double> colPrice = new TableColumn<>("PRICE");
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));

        TableColumn<Trade, String> colStatus = new TableColumn<>("STATUS");
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        tradeTable.getColumns().addAll(colId, colType, colInst, colQty, colPrice, colStatus);
        
        tradeBox.getChildren().addAll(histTitle, tradeTable);
        return tradeBox;
    }

    private void submitTrade(String type, String instrument, String qtyStr, String priceStr) {
        try {
            double quantity = Double.parseDouble(qtyStr);
            double price = Double.parseDouble(priceStr);
            Trade trade = new Trade(type, instrument, quantity, price);
            MainServer.getPortfolio().addTrade(trade);
            refreshPortfolioStats();
            refreshTradeTable();
        } catch (Exception ex) {
            // simple error handling
            System.err.println("Trade Error: " + ex.getMessage());
        }
    }

    private void refreshPortfolioStats() {
        Portfolio p = MainServer.getPortfolio();
        lblTotalTrades.setText(String.valueOf(p.getTradeCount()));
        lblTotalInvested.setText(String.format("₹%.2f", p.getTotalInvested()));
        lblTotalRevenue.setText(String.format("₹%.2f", p.getTotalRevenue()));
        
        double profitLoss = p.calculateProfitLoss();
        lblProfitLoss.setText(String.format("₹%.2f", profitLoss));
        lblProfitLoss.getStyleClass().removeAll("profit-text", "loss-text");
        if (profitLoss >= 0) {
            lblProfitLoss.getStyleClass().add("profit-text");
        } else {
            lblProfitLoss.getStyleClass().add("loss-text");
        }
    }

    private void refreshTradeTable() {
        tradeTable.getItems().clear();
        List<Trade> trades = MainServer.getPortfolio().getTrades();
        tradeTable.getItems().addAll(trades);
    }

    @Override
    public void onPriceUpdate(String instrument, double price, String timestamp) {
        Platform.runLater(() -> {
            Label lblPrice = priceLabels.get(instrument);
            Label lblChange = changeLabels.get(instrument);
            
            if (lblPrice != null && lblChange != null) {
                double prev = previousPrices.getOrDefault(instrument, price);
                double diff = price - prev;
                double pct = (prev == 0) ? 0 : (diff / prev) * 100;
                
                lblPrice.setText(String.format("%.4f", price));
                lblChange.setText(String.format("%s %.3f%%", diff >= 0 ? "▲" : "▼", Math.abs(pct)));
                
                lblChange.getStyleClass().removeAll("profit-text", "loss-text");
                if (diff >= 0) lblChange.getStyleClass().add("profit-text");
                else lblChange.getStyleClass().add("loss-text");
                
                previousPrices.put(instrument, price);
            }
        });
    }

    @Override
    public void onSignalGenerated(Signal signal) {
        // Handled in Strategy View or logged globally
    }
}
