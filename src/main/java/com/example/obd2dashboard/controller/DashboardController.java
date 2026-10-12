package com.example.obd2dashboard.controller;

import com.example.obd2dashboard.config.AppConfig;
import com.example.obd2dashboard.model.ConnectionStatus;
import com.example.obd2dashboard.model.ObdPid;
import com.example.obd2dashboard.parser.ObdResponseParser;
import com.example.obd2dashboard.service.Elm327Service;
import com.example.obd2dashboard.service.ObdEventListener;
import com.example.obd2dashboard.service.ObdService;
import com.example.obd2dashboard.service.SerialPortService;
import com.example.obd2dashboard.service.SimulationService;
import com.example.obd2dashboard.util.Localization;
import com.example.obd2dashboard.util.Localization.Language;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class DashboardController implements ObdEventListener {
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String UPDATE_PATTERN = "dd MMM yyyy HH:mm:ss";

    @FXML private ComboBox<String> portComboBox;
    @FXML private ComboBox<Integer> baudRateComboBox;
    @FXML private ComboBox<Language> languageComboBox;
    @FXML private Button refreshPortsButton;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;
    @FXML private Button clearLogButton;
    @FXML private ToggleButton simulationToggle;
    @FXML private HBox statusPill;
    @FXML private Label statusLabel;
    @FXML private Label lastUpdatedLabel;

    @FXML private Label appTitleLabel;
    @FXML private Label appSubtitleLabel;
    @FXML private Label languageLabel;
    @FXML private Label portFieldLabel;
    @FXML private Label baudFieldLabel;
    @FXML private Label historySectionTitle;
    @FXML private Label logSectionTitle;
    @FXML private Label dtcSectionTitle;
    @FXML private Label dtcSectionSubtitle;

    @FXML private Label rpmCardTitle;
    @FXML private Label speedCardTitle;
    @FXML private Label coolantCardTitle;
    @FXML private Label engineLoadCardTitle;
    @FXML private Label throttleCardTitle;
    @FXML private Label intakeAirCardTitle;
    @FXML private Label fuelCardTitle;
    @FXML private Label manifoldCardTitle;
    @FXML private Label voltageCardTitle;

    @FXML private Label rpmValueLabel;
    @FXML private Label speedValueLabel;
    @FXML private Label coolantValueLabel;
    @FXML private Label engineLoadValueLabel;
    @FXML private Label throttleValueLabel;
    @FXML private Label intakeAirValueLabel;
    @FXML private Label fuelValueLabel;
    @FXML private Label manifoldValueLabel;
    @FXML private Label voltageValueLabel;
    @FXML private ProgressBar rpmProgressBar;
    @FXML private ProgressBar speedProgressBar;

    @FXML private LineChart<Number, Number> rpmChart;
    @FXML private LineChart<Number, Number> speedChart;
    @FXML private NumberAxis rpmXAxis;
    @FXML private NumberAxis rpmYAxis;
    @FXML private NumberAxis speedXAxis;
    @FXML private NumberAxis speedYAxis;
    @FXML private TextArea communicationLog;
    @FXML private ListView<String> dtcListView;
    @FXML private Button readDtcButton;
    private final Label dtcPlaceholderLabel = new Label();

    private final Elm327Service elm327Service;
    private final SimulationService simulationService;
    private final Deque<String> logLines = new ArrayDeque<>();
    private final XYChart.Series<Number, Number> rpmSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> speedSeries = new XYChart.Series<>();
    private long chartStartedAt;
    private volatile boolean shuttingDown;
    private boolean connectionBusy;
    private boolean dtcBusy;
    private ConnectionStatus currentStatus = ConnectionStatus.DISCONNECTED;
    private LocalDateTime lastUpdatedAt;
    private DtcPresentation.State dtcState = DtcPresentation.State.NONE;
    private List<String> dtcResults = List.of();
    private DateTimeFormatter updateTimeFormatter =
            DateTimeFormatter.ofPattern(UPDATE_PATTERN, Localization.locale());
    private Stage stage;

    public DashboardController() {
        elm327Service = new Elm327Service(new SerialPortService(), new ObdResponseParser(), this);
        simulationService = new SimulationService(this);
    }

    @FXML
    private void initialize() {
        baudRateComboBox.setItems(FXCollections.observableArrayList(AppConfig.BAUD_RATES));
        baudRateComboBox.setValue(AppConfig.DEFAULT_BAUD_RATE);
        communicationLog.setEditable(false);
        communicationLog.setWrapText(true);
        rpmSeries.setName(Localization.text("chart.series.rpm"));
        speedSeries.setName(Localization.text("chart.series.speed"));
        rpmChart.getData().add(rpmSeries);
        speedChart.getData().add(speedSeries);
        rpmChart.setCreateSymbols(false);
        speedChart.setCreateSymbols(false);
        chartStartedAt = System.currentTimeMillis();
        configureDtcList();
        configureLanguageSelector();
        setStatus(ConnectionStatus.DISCONNECTED);
        applyLocalization();
        refreshPorts();
        appendLog("INFO", Localization.text("log.dashboardReady"));
    }

    private void configureLanguageSelector() {
        languageComboBox.setItems(FXCollections.observableArrayList(Language.values()));
        languageComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Language language) {
                return language == null ? "" : language.displayName();
            }

            @Override
            public Language fromString(String string) {
                return null;
            }
        });
        languageComboBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Language language, boolean empty) {
                super.updateItem(language, empty);
                setText(empty || language == null ? null : language.displayName());
            }
        });
        languageComboBox.setValue(Localization.language());
        languageComboBox.valueProperty().addListener((observable, previous, selected) -> {
            if (selected != null && selected != Localization.language()) {
                Localization.setLanguage(selected);
                applyLocalization();
            }
        });
    }

    private void configureDtcList() {
        dtcPlaceholderLabel.getStyleClass().add("dtc-empty");
        dtcListView.setPlaceholder(dtcPlaceholderLabel);
    }

    /** Menerapkan bahasa aktif ke seluruh teks statis tanpa memuat ulang FXML. */
    private void applyLocalization() {
        if (stage != null) {
            stage.setTitle(Localization.text("app.title"));
        }
        appTitleLabel.setText(Localization.text("header.title"));
        appSubtitleLabel.setText(Localization.text("header.subtitle"));
        languageLabel.setText(Localization.text("language.label"));
        portFieldLabel.setText(Localization.text("toolbar.port"));
        baudFieldLabel.setText(Localization.text("toolbar.baud"));
        refreshPortsButton.setText(Localization.text("button.refreshPorts"));
        connectButton.setText(Localization.text("button.connect"));
        disconnectButton.setText(Localization.text("button.disconnect"));
        simulationToggle.setText(Localization.text("button.simulation"));
        clearLogButton.setText(Localization.text("button.clearLog"));
        readDtcButton.setText(Localization.text("button.readDtc"));

        historySectionTitle.setText(Localization.text("section.history"));
        logSectionTitle.setText(Localization.text("section.log"));
        dtcSectionTitle.setText(Localization.text("section.dtc"));
        dtcSectionSubtitle.setText(Localization.text("section.dtc.subtitle"));

        rpmCardTitle.setText(Localization.text("card.ENGINE_RPM"));
        speedCardTitle.setText(Localization.text("card.VEHICLE_SPEED"));
        coolantCardTitle.setText(Localization.text("card.COOLANT_TEMPERATURE"));
        engineLoadCardTitle.setText(Localization.text("card.ENGINE_LOAD"));
        throttleCardTitle.setText(Localization.text("card.THROTTLE_POSITION"));
        intakeAirCardTitle.setText(Localization.text("card.INTAKE_AIR_TEMPERATURE"));
        fuelCardTitle.setText(Localization.text("card.FUEL_LEVEL"));
        manifoldCardTitle.setText(Localization.text("card.MANIFOLD_PRESSURE"));
        voltageCardTitle.setText(Localization.text("card.VOLTAGE"));

        rpmChart.setTitle(Localization.text("chart.rpm.title"));
        speedChart.setTitle(Localization.text("chart.speed.title"));
        rpmXAxis.setLabel(Localization.text("chart.axis.seconds"));
        speedXAxis.setLabel(Localization.text("chart.axis.seconds"));
        rpmYAxis.setLabel(Localization.text("chart.axis.rpm"));
        speedYAxis.setLabel(Localization.text("chart.axis.speed"));
        rpmSeries.setName(Localization.text("chart.series.rpm"));
        speedSeries.setName(Localization.text("chart.series.speed"));

        updateTimeFormatter = DateTimeFormatter.ofPattern(UPDATE_PATTERN, Localization.locale());
        refreshStatusText();
        refreshLastUpdatedText();
        refreshDtcList();
    }

    private void refreshStatusText() {
        String port = portComboBox.getValue();
        String text = switch (currentStatus) {
            case CONNECTING -> port == null
                    ? Localization.text("status.connecting.generic")
                    : Localization.text("status.connecting", port);
            case CONNECTED -> port == null
                    ? Localization.text("status.connected.generic")
                    : Localization.text("status.connected", port);
            case SIMULATION -> Localization.text("status.simulation");
            case ERROR -> Localization.text("status.error");
            case DISCONNECTED -> Localization.text("status.disconnected");
        };
        statusLabel.setText(text);
    }

    private void refreshLastUpdatedText() {
        lastUpdatedLabel.setText(lastUpdatedAt == null
                ? Localization.text("lastUpdated.none")
                : Localization.text("lastUpdated.prefix", updateTimeFormatter.format(lastUpdatedAt)));
    }

    /** Merender ulang daftar DTC sesuai state aktif agar tidak menampilkan kode basi. */
    private void refreshDtcList() {
        DtcPresentation.Display display = DtcPresentation.display(
                dtcState, dtcResults, dtcMessage());
        // Kode DTC asli hanya ada di items; status (loading/empty/no-connection/error) hanya di
        // placeholder sehingga tidak dapat dipilih sebagai baris.
        dtcPlaceholderLabel.setText(display.placeholder() ? display.message() : null);
        dtcListView.setItems(FXCollections.observableArrayList(display.items()));
    }

    private String dtcMessage() {
        return switch (dtcState) {
            case LOADING -> Localization.text("dtc.loading");
            case EMPTY -> Localization.text("dtc.empty");
            case NO_CONNECTION -> Localization.text("dtc.noConnection");
            case ERROR -> Localization.text("log.dtcReadError");
            case NONE, RESULTS -> null;
        };
    }

    @FXML
    private void refreshPorts() {
        String selected = portComboBox.getValue();
        List<String> ports = elm327Service.listAvailablePorts();
        portComboBox.setItems(FXCollections.observableArrayList(ports));
        if (selected != null && ports.contains(selected)) {
            portComboBox.setValue(selected);
        } else if (!ports.isEmpty()) {
            portComboBox.getSelectionModel().selectFirst();
        }
        appendLog("INFO", ports.isEmpty()
                ? Localization.text("log.noPorts")
                : Localization.text("log.portsAvailable", String.join(", ", ports)));
    }

    @FXML
    private void connect() {
        if (shuttingDown || connectionBusy) {
            return;
        }
        if (elm327Service.isRunning()) {
            appendLog("WARN", Localization.text("log.connectionActive"));
            return;
        }
        String port = portComboBox.getValue();
        Integer baudRate = baudRateComboBox.getValue();
        if (port == null || baudRate == null) {
            setStatus(ConnectionStatus.ERROR);
            appendLog("ERROR", Localization.text("log.portBaudMissing"));
            return;
        }

        setConnectionControlsBusy(true);
        simulationToggle.setSelected(false);
        CompletableFuture<Void> preparation;
        try {
            preparation = simulationService.isRunning()
                    ? simulationService.stop()
                    : CompletableFuture.completedFuture(null);
        } catch (RuntimeException exception) {
            setConnectionControlsBusy(false);
            appendLog("ERROR", rootMessage(exception));
            return;
        }
        preparation.whenComplete((ignored, stopError) -> Platform.runLater(() -> {
            if (shuttingDown) {
                return;
            }
            if (stopError != null) {
                setConnectionControlsBusy(false);
                appendLog("ERROR", rootMessage(stopError));
                return;
            }
            resetValueLabels();
            CompletableFuture<Void> connection;
            try {
                connection = elm327Service.connect(port, baudRate);
            } catch (RuntimeException exception) {
                setConnectionControlsBusy(false);
                appendLog("ERROR", rootMessage(exception));
                return;
            }
            connection.whenComplete((ignoredConnect, connectError) -> Platform.runLater(() -> {
                if (shuttingDown) {
                    return;
                }
                setConnectionControlsBusy(false);
                if (connectError != null) {
                    appendLog("ERROR", rootMessage(connectError));
                }
            }));
        }));
    }

    @FXML
    private void disconnect() {
        if (shuttingDown || connectionBusy) {
            return;
        }
        ObdService activeService = activeService();
        if (activeService == null) {
            appendLog("WARN", Localization.text("log.noActiveConnection"));
            return;
        }
        setConnectionControlsBusy(true);
        CompletableFuture<Void> stopFuture;
        try {
            stopFuture = activeService.stop();
        } catch (RuntimeException exception) {
            setConnectionControlsBusy(false);
            appendLog("ERROR", rootMessage(exception));
            return;
        }
        stopFuture.whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (shuttingDown) {
                return;
            }
            setConnectionControlsBusy(false);
            if (error != null) {
                appendLog("ERROR", rootMessage(error));
                return;
            }
            simulationToggle.setSelected(false);
            resetValueLabels();
        }));
    }

    @FXML
    private void toggleSimulation() {
        if (shuttingDown || connectionBusy) {
            return;
        }
        if (simulationToggle.isSelected()) {
            setConnectionControlsBusy(true);
            CompletableFuture<Void> preparation;
            try {
                preparation = elm327Service.isRunning()
                        ? elm327Service.stop()
                        : CompletableFuture.completedFuture(null);
            } catch (RuntimeException exception) {
                setConnectionControlsBusy(false);
                simulationToggle.setSelected(false);
                appendLog("ERROR", rootMessage(exception));
                return;
            }
            preparation.whenComplete((ignored, error) -> Platform.runLater(() -> {
                if (shuttingDown) {
                    return;
                }
                if (error != null) {
                    setConnectionControlsBusy(false);
                    simulationToggle.setSelected(false);
                    appendLog("ERROR", rootMessage(error));
                    return;
                }
                resetValueLabels();
                try {
                    simulationService.start();
                } catch (RuntimeException exception) {
                    setConnectionControlsBusy(false);
                    simulationToggle.setSelected(false);
                    appendLog("ERROR", rootMessage(exception));
                    return;
                }
                setConnectionControlsBusy(false);
            }));
        } else if (simulationService.isRunning()) {
            setConnectionControlsBusy(true);
            CompletableFuture<Void> stopFuture;
            try {
                stopFuture = simulationService.stop();
            } catch (RuntimeException exception) {
                setConnectionControlsBusy(false);
                appendLog("ERROR", rootMessage(exception));
                return;
            }
            stopFuture.whenComplete((ignored, error) -> Platform.runLater(() -> {
                if (shuttingDown) {
                    return;
                }
                setConnectionControlsBusy(false);
                if (error != null) {
                    appendLog("ERROR", rootMessage(error));
                    return;
                }
                resetValueLabels();
            }));
        }
    }

    @FXML
    private void readDtc() {
        if (shuttingDown || connectionBusy || dtcBusy) {
            return;
        }
        ObdService activeService = activeService();
        if (activeService == null) {
            dtcState = DtcPresentation.State.NO_CONNECTION;
            refreshDtcList();
            appendLog("WARN", Localization.text("log.noConnectionForDtc"));
            return;
        }
        dtcBusy = true;
        // Buang hasil lama sebelum pembacaan dicoba agar kode basi tidak tampak sebagai hasil baru.
        dtcState = DtcPresentation.State.LOADING;
        dtcResults = List.of();
        refreshDtcList();
        updateControlState();
        CompletableFuture<List<String>> future;
        try {
            future = activeService.readDtc();
        } catch (RuntimeException exception) {
            dtcBusy = false;
            showDtcError(rootMessage(exception));
            updateControlState();
            return;
        }
        future.whenComplete((dtcs, error) -> Platform.runLater(() -> {
            if (shuttingDown) {
                return;
            }
            dtcBusy = false;
            if (error != null) {
                showDtcError(rootMessage(error));
            } else {
                showDtcResults(dtcs);
            }
            updateControlState();
        }));
    }

    private void showDtcError(String message) {
        dtcState = DtcPresentation.State.ERROR;
        dtcResults = List.of();
        refreshDtcList();
        appendLog("ERROR", Localization.text("log.dtcReadFailed", message));
    }

    private void showDtcResults(List<String> dtcs) {
        dtcResults = List.copyOf(dtcs);
        dtcState = dtcs.isEmpty() ? DtcPresentation.State.EMPTY : DtcPresentation.State.RESULTS;
        refreshDtcList();
        appendLog("INFO", dtcs.isEmpty()
                ? Localization.text("log.dtcNone")
                : Localization.text("log.dtcFound", String.join(", ", dtcs)));
    }

    @FXML
    private void clearLog() {
        logLines.clear();
        communicationLog.clear();
    }

    @Override
    public void onTelemetry(ObdPid pid, double value) {
        if (shuttingDown) {
            return;
        }
        Platform.runLater(() -> {
            if (shuttingDown) {
                return;
            }
            applyTelemetry(pid, value);
        });
    }

    private void applyTelemetry(ObdPid pid, double value) {
        lastUpdatedAt = LocalDateTime.now();
        refreshLastUpdatedText();

        switch (pid) {
            case ENGINE_RPM -> {
                rpmValueLabel.setText(format(value, 0));
                rpmProgressBar.setProgress(clamp01(value / 6000.0));
                addChartPoint(rpmSeries, value);
            }
            case VEHICLE_SPEED -> {
                speedValueLabel.setText(format(value, 0));
                speedProgressBar.setProgress(clamp01(value / 200.0));
                addChartPoint(speedSeries, value);
            }
            case COOLANT_TEMPERATURE -> {
                coolantValueLabel.setText(format(value, 0));
            }
            case ENGINE_LOAD -> {
                engineLoadValueLabel.setText(format(value, 1));
            }
            case THROTTLE_POSITION -> {
                throttleValueLabel.setText(format(value, 1));
            }
            case INTAKE_AIR_TEMPERATURE -> {
                intakeAirValueLabel.setText(format(value, 0));
            }
            case FUEL_LEVEL -> {
                fuelValueLabel.setText(format(value, 1));
            }
            case MANIFOLD_PRESSURE -> {
                manifoldValueLabel.setText(format(value, 0));
            }
            case VOLTAGE -> {
                voltageValueLabel.setText(format(value, 2));
            }
        }
    }

    private void addChartPoint(XYChart.Series<Number, Number> series, double value) {
        double elapsedSeconds = (System.currentTimeMillis() - chartStartedAt) / 1000.0;
        series.getData().add(new XYChart.Data<>(elapsedSeconds, value));
        if (series.getData().size() > AppConfig.MAX_CHART_POINTS) {
            series.getData().removeFirst();
        }
    }

    @Override
    public void onStatus(ConnectionStatus status, String message) {
        if (!shuttingDown) {
            Platform.runLater(() -> {
                if (shuttingDown) {
                    return;
                }
                setStatus(status);
                updateControlState();
            });
        }
    }

    @Override
    public void onLog(String level, String message) {
        if (!shuttingDown) {
            Platform.runLater(() -> {
                if (shuttingDown) {
                    return;
                }
                appendLog(level, message);
            });
        }
    }

    private void setStatus(ConnectionStatus status) {
        currentStatus = status;
        refreshStatusText();
        statusPill.getStyleClass().removeAll(
                "status-disconnected", "status-connecting", "status-connected",
                "status-simulation", "status-error");
        String styleClass = switch (status) {
            case CONNECTED -> "status-connected";
            case CONNECTING -> "status-connecting";
            case SIMULATION -> "status-simulation";
            case ERROR -> "status-error";
            case DISCONNECTED -> "status-disconnected";
        };
        statusPill.getStyleClass().add(styleClass);
    }

    private void appendLog(String level, String message) {
        String line = String.format("[%s] [%s] %s", LOG_TIME.format(LocalDateTime.now()), level, message);
        logLines.addLast(line);
        while (logLines.size() > AppConfig.MAX_LOG_LINES) {
            logLines.removeFirst();
        }
        communicationLog.setText(String.join(System.lineSeparator(), logLines));
        communicationLog.positionCaret(communicationLog.getLength());
    }

    private void setConnectionControlsBusy(boolean busy) {
        connectionBusy = busy;
        updateControlState();
    }

    private void updateControlState() {
        boolean real = elm327Service.isRunning();
        boolean simulation = simulationService.isRunning();
        boolean active = real || simulation;
        portComboBox.setDisable(connectionBusy || active);
        baudRateComboBox.setDisable(connectionBusy || active);
        refreshPortsButton.setDisable(connectionBusy || active);
        connectButton.setDisable(connectionBusy || active);
        disconnectButton.setDisable(connectionBusy || !active);
        simulationToggle.setDisable(connectionBusy);
        readDtcButton.setDisable(connectionBusy || dtcBusy || !active);
        if (!connectionBusy) {
            simulationToggle.setSelected(simulation);
        }
    }

    private ObdService activeService() {
        if (elm327Service.isRunning()) {
            return elm327Service;
        }
        if (simulationService.isRunning()) {
            return simulationService;
        }
        return null;
    }

    private void resetValueLabels() {
        for (Label label : List.of(rpmValueLabel, speedValueLabel, coolantValueLabel,
                engineLoadValueLabel, throttleValueLabel, intakeAirValueLabel,
                fuelValueLabel, manifoldValueLabel, voltageValueLabel)) {
            label.setText("--");
        }
        rpmProgressBar.setProgress(0);
        speedProgressBar.setProgress(0);
        lastUpdatedAt = null;
        refreshLastUpdatedText();
        dtcState = DtcPresentation.State.NONE;
        dtcResults = List.of();
        refreshDtcList();
        clearCharts();
    }

    private void clearCharts() {
        rpmSeries.getData().clear();
        speedSeries.getData().clear();
        chartStartedAt = System.currentTimeMillis();
    }

    private String format(double value, int decimals) {
        return String.format(Localization.locale(), "%." + decimals + "f", value);
    }

    private double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    /** Dipanggil oleh {@code Main} setelah FXML dimuat agar judul jendela ikut terlokalisasi. */
    public void setStage(Stage stage) {
        this.stage = stage;
        stage.setTitle(Localization.text("app.title"));
    }

    public void shutdown() {
        shuttingDown = true;
        simulationService.close();
        elm327Service.close();
    }
}
