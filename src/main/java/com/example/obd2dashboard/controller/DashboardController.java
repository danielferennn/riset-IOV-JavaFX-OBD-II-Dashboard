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

    private final Elm327Service elm327Service;
    private final SimulationService simulationService;
    private final Deque<String> logLines = new ArrayDeque<>();
    private final XYChart.Series<Number, Number> rpmSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> speedSeries = new XYChart.Series<>();
    private long chartStartedAt;
    private volatile boolean shuttingDown;
    private boolean connectionBusy;
    private boolean dtcBusy;

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
        if (shuttingDown || connectionBusy) {
            return;
        }
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
            appendLog("WARN", "Tidak ada koneksi aktif");
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
            appendLog("WARN", "Aktifkan koneksi atau simulasi sebelum membaca DTC");
            return;
        }
        dtcBusy = true;
        updateControlState();
        CompletableFuture<List<String>> future;
        try {
            future = activeService.readDtc();
        } catch (RuntimeException exception) {
            dtcBusy = false;
            updateControlState();
            appendLog("ERROR", "Gagal membaca DTC: " + rootMessage(exception));
            return;
        }
        future.whenComplete((dtcs, error) -> Platform.runLater(() -> {
            if (shuttingDown) {
                return;
            }
            dtcBusy = false;
            updateControlState();
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
        Platform.runLater(() -> {
            if (shuttingDown) {
                return;
            }
            applyTelemetry(pid, value);
        });
    }

    private void applyTelemetry(ObdPid pid, double value) {
        LocalDateTime now = LocalDateTime.now();
        lastUpdatedLabel.setText("Updated " + UPDATE_TIME.format(now));

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
                setStatus(status, message);
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

    private void setStatus(ConnectionStatus status, String message) {
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
