import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class RiskView extends VBox {

    private TextField txtStopLoss;
    private TextField txtTakeProfit;
    private TextField txtMaxDrawdown;
    private TextField txtRiskPerTrade;
    private Label lblCurrentRisk;

    public RiskView() {
        setSpacing(20);

        Label title = new Label("Risk Management Settings");
        title.getStyleClass().add("title-label");

        // --- Configuration Form ---
        VBox formBox = new VBox(15);
        formBox.getStyleClass().add("glass-card");
        formBox.setPrefWidth(400);
        formBox.setMaxWidth(400);

        txtStopLoss = new TextField(String.valueOf(MainServer.getRiskManager().getStopLossPercent()));
        txtTakeProfit = new TextField(String.valueOf(MainServer.getRiskManager().getTakeProfitPercent()));
        txtMaxDrawdown = new TextField(String.valueOf(MainServer.getRiskManager().getMaxDrawdownPercent()));
        txtRiskPerTrade = new TextField(String.valueOf(MainServer.getRiskManager().getRiskPerTradePercent()));

        Button btnApply = new Button("Update Risk Limits");
        btnApply.getStyleClass().add("button-primary");
        btnApply.setOnAction(e -> updateRiskSettings());

        formBox.getChildren().addAll(
            new Label("Global Stop Loss (%):"), txtStopLoss,
            new Label("Global Take Profit (%):"), txtTakeProfit,
            new Label("Max Portfolio Drawdown (%):"), txtMaxDrawdown,
            new Label("Risk Per Trade (%):"), txtRiskPerTrade,
            btnApply
        );

        // --- Current Status Display ---
        VBox statusBox = new VBox(15);
        statusBox.getStyleClass().add("glass-card");
        HBox.setHgrow(statusBox, Priority.ALWAYS);

        Label statusTitle = new Label("Current Risk State");
        statusTitle.getStyleClass().add("header-label");

        lblCurrentRisk = new Label();
        lblCurrentRisk.getStyleClass().add("subtitle-label");
        lblCurrentRisk.setStyle("-fx-font-size: 14px;");
        refreshStatusDisplay();

        statusBox.getChildren().addAll(statusTitle, lblCurrentRisk);

        HBox split = new HBox(20);
        split.getChildren().addAll(formBox, statusBox);

        getChildren().addAll(title, split);
    }

    private void updateRiskSettings() {
        try {
            RiskManager rm = MainServer.getRiskManager();
            rm.setStopLossPercent(Double.parseDouble(txtStopLoss.getText()));
            rm.setTakeProfitPercent(Double.parseDouble(txtTakeProfit.getText()));
            rm.setMaxDrawdownPercent(Double.parseDouble(txtMaxDrawdown.getText()));
            rm.setRiskPerTradePercent(Double.parseDouble(txtRiskPerTrade.getText()));

            refreshStatusDisplay();
        } catch (NumberFormatException ex) {
            lblCurrentRisk.setText("Error: Invalid numeric input.");
        }
    }

    private void refreshStatusDisplay() {
        RiskManager rm = MainServer.getRiskManager();
        String status = "Active Settings:\n" +
                        "• Stop Loss: " + rm.getStopLossPercent() + "%\n" +
                        "• Take Profit: " + rm.getTakeProfitPercent() + "%\n" +
                        "• Max Drawdown: " + rm.getMaxDrawdownPercent() + "%\n" +
                        "• Risk Per Trade: " + rm.getRiskPerTradePercent() + "%\n\n" +
                        "System State: " + (rm.isDrawdownExceeded() ? "HALTED (Max Drawdown Exceeded)" : "OPERATIONAL");
        lblCurrentRisk.setText(status);
        
        if (rm.isDrawdownExceeded()) {
            lblCurrentRisk.getStyleClass().add("loss-text");
            lblCurrentRisk.getStyleClass().remove("profit-text");
        } else {
            lblCurrentRisk.getStyleClass().add("profit-text");
            lblCurrentRisk.getStyleClass().remove("loss-text");
        }
    }
}
