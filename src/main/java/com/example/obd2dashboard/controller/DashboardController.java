package com.example.obd2dashboard.controller;

import com.example.obd2dashboard.config.AppConfig;
import com.example.obd2dashboard.model.ConnectionStatus;
import com.example.obd2dashboard.model.ObdPid;
import com.example.obd2dashboard.model.TelemetryData;
import com.example.obd2dashboard.parser.ObdResponseParser;
import com.example.obd2dashboard.service.Elm327Service;
import com.example.obd2dashboard.service.ObdEventListener;
import com.example.obd2dashboard.service.ObdService;
import com.example.obd2dashboard.service.SerialPortService;
import com.example.obd2dashboard.service.SimulationService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class DashboardController implements ObdEventListener {
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter UPDATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");

    @FXML private ComboBox<String> portComboBox;
    @FXML private ComboBox<Integer> baudRateComboBox;
    @FXML private Button refreshPortsButton;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;
    @FXML private ToggleButton simulationToggle;
    @FXML private HBox statusPill;
    @FXML private Label statusLabel;
    @FXML private Label lastUpdatedLabel;

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
    @FXML private TextArea communicationLog;
    @FXML private ListView<String> dtcListView;
    @FXML private Button readDtcButton;

    private final TelemetryData telemetryData = new TelemetryData();
    private final Elm327Service elm327Service;
    private final SimulationService simulationService;
    private final Deque<String> logLines = new ArrayDeque<>();
    private final XYChart.Series<Number, Number> rpmSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> speedSeries = new XYChart.Series<>();
    private long chartStartedAt;
    private volatile boolean shuttingDown;

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
        rpmSeries.setName("RPM");
        speedSeries.setName("Speed");
        rpmChart.getData().add(rpmSeries);
        speedChart.getData().add(speedSeries);
        rpmChart.setCreateSymbols(false);
        speedChart.setCreateSymbols(false);
        chartStartedAt = System.currentTimeMillis();
        setStatus(ConnectionStatus.DISCONNECTED, "Disconnected");
        refreshPorts();
        appendLog("INFO", "Dashboard siap");
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
                ? "Tidak ada port serial terdeteksi"
                : "Port tersedia: " + String.join(", ", ports));
    }

    @FXML
    private void connect() {
        if (elm327Service.isRunning()) {
            appendLog("WARN", "Koneksi nyata sudah aktif");
            return;
        }
        String port = portComboBox.getValue();
        Integer baudRate = baudRateComboBox.getValue();
        if (port == null || baudRate == null) {
            setStatus(ConnectionStatus.ERROR, "Pilih port dan baud rate");
            appendLog("ERROR", "Port serial atau baud rate belum dipilih");
            return;
        }

        CompletableFuture<Void> preparation = simulationService.isRunning()
                ? simulationService.stop()
                : CompletableFuture.completedFuture(null);
        simulationToggle.setSelected(false);
        setConnectionControlsBusy(true);
        preparation.thenCompose(ignored -> elm327Service.connect(port, baudRate))
                .whenComplete((ignored, error) -> Platform.runLater(() -> {
                    setConnectionControlsBusy(false);
                    updateControlState();
                    if (error != null) {
                        appendLog("ERROR", rootMessage(error));
                    }
                }));
    }

    @FXML
    private void disconnect() {
        ObdService activeService = activeService();
        if (activeService == null) {
            appendLog("WARN", "Tidak ada koneksi aktif");
            return;
        }
        setConnectionControlsBusy(true);
        activeService.stop().whenComplete((ignored, error) -> Platform.runLater(() -> {
            simulationToggle.setSelected(false);
            setConnectionControlsBusy(false);
            updateControlState();
            telemetryData.clearSensorValues();
            resetValueLabels();
            if (error != null) {
                appendLog("ERROR", rootMessage(error));
            }
        }));
    }

    @FXML
    private void toggleSimulation() {
        if (simulationToggle.isSelected()) {
            CompletableFuture<Void> preparation = elm327Service.isRunning()
                    ? elm327Service.stop()
                    : CompletableFuture.completedFuture(null);
            preparation.whenComplete((ignored, error) -> Platform.runLater(() -> {
                if (error != null) {
                    simulationToggle.setSelected(false);
                    appendLog("ERROR", rootMessage(error));
                    return;
                }
                telemetryData.clearSensorValues();
                clearCharts();
                simulationService.start();
                updateControlState();
            }));
        } else if (simulationService.isRunning()) {
            simulationService.stop();
            telemetryData.clearSensorValues();
            resetValueLabels();
            updateControlState();
        }
    }

    @FXML
    private void readDtc() {
        ObdService activeService = activeService();
        if (activeService == null) {
            appendLog("WARN", "Aktifkan koneksi atau simulasi sebelum membaca DTC");
            return;
        }
        readDtcButton.setDisable(true);
        activeService.readDtc().whenComplete((dtcs, error) -> Platform.runLater(() -> {
            readDtcButton.setDisable(false);
            if (error != null) {
                appendLog("ERROR", "Gagal membaca DTC: " + rootMessage(error));
                return;
            }
            dtcListView.setItems(FXCollections.observableArrayList(
                    dtcs.isEmpty() ? List.of("Tidak ada DTC") : dtcs));
            appendLog("INFO", dtcs.isEmpty()
                    ? "Tidak ada DTC"
                    : "DTC ditemukan: " + String.join(", ", dtcs));
        }));
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
        Platform.runLater(() -> applyTelemetry(pid, value));
    }

    private void applyTelemetry(ObdPid pid, double value) {
        LocalDateTime now = LocalDateTime.now();
        telemetryData.setLastUpdated(now);
        lastUpdatedLabel.setText("Updated " + UPDATE_TIME.format(now));

        switch (pid) {
            case ENGINE_RPM -> {
                telemetryData.setRpm(value);
                rpmValueLabel.setText(format(value, 0));
                rpmProgressBar.setProgress(clamp01(value / 6000.0));
                addChartPoint(rpmSeries, value);
            }
            case VEHICLE_SPEED -> {
                telemetryData.setSpeedKmh(value);
                speedValueLabel.setText(format(value, 0));
                speedProgressBar.setProgress(clamp01(value / 200.0));
                addChartPoint(speedSeries, value);
            }
            case COOLANT_TEMPERATURE -> {
                telemetryData.setCoolantTemperatureC(value);
                coolantValueLabel.setText(format(value, 0));
            }
            case ENGINE_LOAD -> {
                telemetryData.setEngineLoadPercent(value);
                engineLoadValueLabel.setText(format(value, 1));
            }
            case THROTTLE_POSITION -> {
                telemetryData.setThrottlePositionPercent(value);
                throttleValueLabel.setText(format(value, 1));
            }
            case INTAKE_AIR_TEMPERATURE -> {
                telemetryData.setIntakeAirTemperatureC(value);
                intakeAirValueLabel.setText(format(value, 0));
            }
            case FUEL_LEVEL -> {
                telemetryData.setFuelLevelPercent(value);
                fuelValueLabel.setText(format(value, 1));
            }
            case MANIFOLD_PRESSURE -> {
                telemetryData.setManifoldPressureKpa(value);
                manifoldValueLabel.setText(format(value, 0));
            }
            case VOLTAGE -> {
                telemetryData.setVoltage(value);
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
                setStatus(status, message);
                updateControlState();
            });
        }
    }

    @Override
    public void onLog(String level, String message) {
        if (!shuttingDown) {
            Platform.runLater(() -> appendLog(level, message));
        }
    }

    private void setStatus(ConnectionStatus status, String message) {
        telemetryData.setConnectionStatus(status);
        statusLabel.setText(message);
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
        connectButton.setDisable(busy);
        disconnectButton.setDisable(busy);
        refreshPortsButton.setDisable(busy);
        portComboBox.setDisable(busy);
        baudRateComboBox.setDisable(busy);
        simulationToggle.setDisable(busy);
    }

    private void updateControlState() {
        boolean real = elm327Service.isRunning();
        boolean simulation = simulationService.isRunning();
        boolean active = real || simulation;
        portComboBox.setDisable(active);
        baudRateComboBox.setDisable(active);
        refreshPortsButton.setDisable(active);
        connectButton.setDisable(active);
        disconnectButton.setDisable(!active);
        readDtcButton.setDisable(!active);
        simulationToggle.setSelected(simulation);
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
        lastUpdatedLabel.setText("Belum ada data");
        dtcListView.getItems().clear();
        clearCharts();
    }

    private void clearCharts() {
        rpmSeries.getData().clear();
        speedSeries.getData().clear();
        chartStartedAt = System.currentTimeMillis();
    }

    private String format(double value, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f", value);
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

    public void shutdown() {
        shuttingDown = true;
        simulationService.close();
        elm327Service.close();
    }
}
