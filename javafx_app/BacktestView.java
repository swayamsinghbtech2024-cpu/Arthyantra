import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class BacktestView extends VBox {

    private ComboBox<String> cmbInstrument;
    private TextField txtDataPoints;
    private TextArea txtResults;

    public BacktestView() {
        setSpacing(20);

        Label title = new Label("Strategy Backtesting Simulator");
        title.getStyleClass().add("title-label");

        // --- Controls Panel ---
        HBox controlBox = new HBox(15);
        controlBox.getStyleClass().add("glass-card");

        cmbInstrument = new ComboBox<>();
        cmbInstrument.getItems().addAll("USD/INR", "EUR/USD", "GBP/USD", "USD/JPY", "GOLD", "NIFTY 50");
        cmbInstrument.setValue("USD/INR");

        txtDataPoints = new TextField("500");
        txtDataPoints.setPromptText("Historical Data Points (e.g. 500)");

        Button btnRun = new Button("Run Backtest");
        btnRun.getStyleClass().add("button-primary");
        btnRun.setOnAction(e -> runBacktest());

        controlBox.getChildren().addAll(
            new Label("Instrument:"), cmbInstrument,
            new Label("Data Points:"), txtDataPoints,
            btnRun
        );

        // --- Results Panel ---
        VBox resultBox = new VBox(15);
        resultBox.getStyleClass().add("glass-card");
        VBox.setVgrow(resultBox, Priority.ALWAYS);

        Label resTitle = new Label("Backtest Results");
        resTitle.getStyleClass().add("header-label");

        txtResults = new TextArea();
        txtResults.setEditable(false);
        txtResults.setStyle("-fx-control-inner-background: #0b1326; -fx-text-fill: #dae2fd; -fx-font-family: monospace; -fx-font-size: 14px;");
        VBox.setVgrow(txtResults, Priority.ALWAYS);

        resultBox.getChildren().addAll(resTitle, txtResults);

        getChildren().addAll(title, controlBox, resultBox);
    }

    private void runBacktest() {
        txtResults.setText("Running backtest... Please wait.\n");
        
        // Run in background to avoid freezing UI
        new Thread(() -> {
            try {
                String instrument = cmbInstrument.getValue();
                int dataPoints = Integer.parseInt(txtDataPoints.getText());
                
                String resultJson = BacktestEngine.compareStrategies(instrument, dataPoints);
                
                // Format the JSON roughly for the UI
                String formatted = resultJson.replace("{", "{\n  ")
                                             .replace(",", ",\n  ")
                                             .replace("}", "\n}");

                Platform.runLater(() -> txtResults.setText("--- Backtest Completed ---\n\n" + formatted));
            } catch (Exception ex) {
                Platform.runLater(() -> txtResults.setText("Error: " + ex.getMessage()));
            }
        }).start();
    }
}
