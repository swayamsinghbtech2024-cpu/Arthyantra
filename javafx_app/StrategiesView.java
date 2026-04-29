import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class StrategiesView extends VBox implements MarketObserver {

    private ComboBox<String> cmbStrategy;
    private CheckBox chkAutoTrade;
    private TextField txtConfidence;
    private TextArea txtSignalLog;

    public StrategiesView() {
        setSpacing(20);

        Label title = new Label("Algorithmic Trading Strategies");
        title.getStyleClass().add("title-label");

        // --- Configuration Panel ---
        VBox configPanel = new VBox(15);
        configPanel.getStyleClass().add("glass-card");
        
        Label configTitle = new Label("Strategy Configuration");
        configTitle.getStyleClass().add("header-label");

        cmbStrategy = new ComboBox<>();
        cmbStrategy.getItems().addAll("Moving Average (MA)", "Relative Strength Index (RSI)");
        
        // Find current strategy
        String currentStrat = MainServer.getMarketSimulator().getActiveStrategy().getName();
        if (currentStrat.contains("RSI")) {
            cmbStrategy.setValue("Relative Strength Index (RSI)");
        } else {
            cmbStrategy.setValue("Moving Average (MA)");
        }

        chkAutoTrade = new CheckBox("Enable Auto-Trade Execution");
        chkAutoTrade.setSelected(MainServer.getTradeEngine().isAutoTradeEnabled());
        chkAutoTrade.setStyle("-fx-text-fill: #dae2fd;");

        txtConfidence = new TextField(String.valueOf(MainServer.getTradeEngine().getConfidenceThreshold()));
        txtConfidence.setPromptText("Confidence Threshold (0.0 to 1.0)");

        Button btnApply = new Button("Apply Configuration");
        btnApply.getStyleClass().add("button-primary");
        btnApply.setOnAction(e -> applyConfig());

        configPanel.getChildren().addAll(
            configTitle, 
            new Label("Active Algorithm:"), cmbStrategy, 
            new Label("Execution:"), chkAutoTrade, 
            new Label("Confidence Threshold:"), txtConfidence, 
            btnApply
        );

        // --- Signal Log Panel ---
        VBox logPanel = new VBox(15);
        logPanel.getStyleClass().add("glass-card");
        VBox.setVgrow(logPanel, Priority.ALWAYS);

        Label logTitle = new Label("Live Signal Log");
        logTitle.getStyleClass().add("header-label");

        txtSignalLog = new TextArea();
        txtSignalLog.setEditable(false);
        txtSignalLog.setStyle("-fx-control-inner-background: #0b1326; -fx-text-fill: #4edea3; -fx-font-family: monospace;");
        VBox.setVgrow(txtSignalLog, Priority.ALWAYS);

        logPanel.getChildren().addAll(logTitle, txtSignalLog);

        getChildren().addAll(title, configPanel, logPanel);

        // Connect to MarketObserver for signals
        MainServer.getMarketSimulator().addObserver(this);
    }

    private void applyConfig() {
        try {
            String selected = cmbStrategy.getValue();
            if (selected.contains("RSI")) {
                MainServer.getMarketSimulator().setStrategy(new RSIStrategy());
            } else {
                MainServer.getMarketSimulator().setStrategy(new MovingAverageStrategy());
            }

            MainServer.getTradeEngine().setAutoTradeEnabled(chkAutoTrade.isSelected());
            MainServer.getTradeEngine().setConfidenceThreshold(Double.parseDouble(txtConfidence.getText()));

            logMsg("Configuration updated successfully.");
        } catch (Exception ex) {
            logMsg("Error updating config: " + ex.getMessage());
        }
    }

    private void logMsg(String msg) {
        Platform.runLater(() -> txtSignalLog.appendText(msg + "\n"));
    }

    @Override
    public void onPriceUpdate(String instrument, double price, String timestamp) {
        // Ignored in this view
    }

    @Override
    public void onSignalGenerated(Signal signal) {
        Platform.runLater(() -> {
            txtSignalLog.appendText(signal.getTimestamp() + " | " + signal.getInstrument() + 
                                  " | " + signal.getType() + " | Confidence: " + 
                                  String.format("%.2f", signal.getConfidence()) + "\n");
        });
    }
}
