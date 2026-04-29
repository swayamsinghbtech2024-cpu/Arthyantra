import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class MainApp extends Application {

    private BorderPane root;
    private StackPane contentArea;

    @Override
    public void start(Stage primaryStage) {
        // Start Backend Server on a background thread
        Thread serverThread = new Thread(() -> {
            try {
                MainServer.main(new String[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true); // close when JavaFX closes
        serverThread.start();

        root = new BorderPane();
        root.getStyleClass().add("root");

        // Top Navigation Bar
        HBox navBar = createNavBar();
        root.setTop(navBar);

        // Content Area
        contentArea = new StackPane();
        contentArea.setPadding(new Insets(20));
        root.setCenter(contentArea);

        // Load Initial View (Delayed slightly to ensure server components instantiate, or DashboardView can handle it dynamically)
        loadView("Dashboard");

        Scene scene = new Scene(root, 1200, 800);
        
        // Load CSS from the same directory
        String css = getClass().getResource("theme.css").toExternalForm();
        scene.getStylesheets().add(css);

        primaryStage.setTitle("ArthyantraFX - JavaFX UI Template");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private HBox createNavBar() {
        HBox navBar = new HBox(15);
        navBar.getStyleClass().add("nav-bar");
        navBar.setAlignment(Pos.CENTER_LEFT);

        Label logo = new Label("Arthyantra");
        logo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #10B981; -fx-padding: 0 30 0 0;");

        Button btnDashboard = createNavBtn("Dashboard");
        Button btnStrategies = createNavBtn("Strategies");
        Button btnBacktest = createNavBtn("Backtest");
        Button btnRisk = createNavBtn("Risk");

        // Set Dashboard active by default
        btnDashboard.getStyleClass().add("active");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnStartSim = new Button("▶ Start Sim");
        btnStartSim.getStyleClass().add("button-primary");
        btnStartSim.setOnAction(e -> {
            if (MainServer.getMarketSimulator().isRunning()) {
                MainServer.getMarketSimulator().stop();
                btnStartSim.setText("▶ Start Sim");
                btnStartSim.setStyle("-fx-background-color: #10B981;");
            } else {
                MainServer.getMarketSimulator().start();
                btnStartSim.setText("⏹ Stop Sim");
                btnStartSim.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white;");
            }
        });

        navBar.getChildren().addAll(logo, btnDashboard, btnStrategies, btnBacktest, btnRisk, spacer, btnStartSim);

        // Navigation logic
        Button[] navButtons = {btnDashboard, btnStrategies, btnBacktest, btnRisk};
        for (Button btn : navButtons) {
            btn.setOnAction(e -> {
                for (Button b : navButtons) b.getStyleClass().remove("active");
                btn.getStyleClass().add("active");
                loadView(btn.getText());
            });
        }

        return navBar;
    }

    private Button createNavBtn(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-button");
        return btn;
    }

    private void loadView(String viewName) {
        contentArea.getChildren().clear();
        switch (viewName) {
            case "Dashboard":
                contentArea.getChildren().add(new DashboardView());
                break;
            case "Strategies":
                contentArea.getChildren().add(new StrategiesView());
                break;
            case "Backtest":
                contentArea.getChildren().add(new BacktestView());
                break;
            case "Risk":
                contentArea.getChildren().add(new RiskView());
                break;
            default:
                Label placeholder = new Label(viewName + " View (Under Construction)");
                placeholder.getStyleClass().add("title-label");
                contentArea.getChildren().add(placeholder);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
