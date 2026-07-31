package com.example.obd2dashboard.service;

import com.example.obd2dashboard.config.AppConfig;
import com.example.obd2dashboard.exception.ObdConnectionException;
import com.example.obd2dashboard.exception.ObdParseException;
import com.example.obd2dashboard.model.ConnectionStatus;
import com.example.obd2dashboard.model.ObdPid;
import com.example.obd2dashboard.parser.ObdResponseParser;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public final class Elm327Service implements ObdService {
    private final SerialPortService serialPortService;
    private final ObdResponseParser parser;
    private final ObdEventListener listener;
    private final ScheduledExecutorService commandExecutor;
    private final List<ScheduledFuture<?>> pollingTasks = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean();

    public Elm327Service(SerialPortService serialPortService,
                         ObdResponseParser parser,
                         ObdEventListener listener) {
        this.serialPortService = serialPortService;
        this.parser = parser;
        this.listener = listener;
        this.commandExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "elm327-command-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public List<String> listAvailablePorts() {
        return serialPortService.listAvailablePorts();
    }

    public CompletableFuture<Void> connect(String portName, int baudRate) {
        if (running.get() || serialPortService.isOpen()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Koneksi ELM327 sudah aktif"));
        }

        listener.onStatus(ConnectionStatus.CONNECTING, "Membuka " + portName);
        return CompletableFuture.runAsync(() -> {
            try {
                serialPortService.openPort(portName, baudRate);
                listener.onLog("INFO", "Port " + portName + " dibuka pada " + baudRate + " baud");
                initializeElm327();
                running.set(true);
                schedulePolling();
                listener.onStatus(ConnectionStatus.CONNECTED, "Connected - " + portName);
            } catch (Exception exception) {
                running.set(false);
                serialPortService.closePort();
                listener.onStatus(ConnectionStatus.ERROR, friendlyMessage(exception));
                listener.onLog("ERROR", exception.getMessage());
                throw new CompletionException(exception);
            }
        }, commandExecutor);
    }

    private void initializeElm327() throws ObdConnectionException {
        List<InitCommand> commands = List.of(
                new InitCommand("ATZ", AppConfig.RESET_TIMEOUT, Duration.ofMillis(700)),
                new InitCommand("ATE0", AppConfig.COMMAND_TIMEOUT, Duration.ZERO),
                new InitCommand("ATL0", AppConfig.COMMAND_TIMEOUT, Duration.ZERO),
                new InitCommand("ATS0", AppConfig.COMMAND_TIMEOUT, Duration.ZERO),
                new InitCommand("ATH0", AppConfig.COMMAND_TIMEOUT, Duration.ZERO),
                new InitCommand("ATSP0", AppConfig.COMMAND_TIMEOUT, Duration.ZERO)
        );
        for (InitCommand initCommand : commands) {
            transact(initCommand.command(), initCommand.timeout());
            if (!initCommand.pauseAfter().isZero()) {
                LockSupport.parkNanos(initCommand.pauseAfter().toNanos());
            }
        }
    }

    private void schedulePolling() {
        schedule(ObdPid.ENGINE_RPM, 0, 500);
        schedule(ObdPid.VEHICLE_SPEED, 100, 500);
        schedule(ObdPid.THROTTLE_POSITION, 200, 1_000);
        schedule(ObdPid.ENGINE_LOAD, 300, 1_000);
        schedule(ObdPid.MANIFOLD_PRESSURE, 400, 1_000);
        schedule(ObdPid.COOLANT_TEMPERATURE, 500, 2_000);
        schedule(ObdPid.INTAKE_AIR_TEMPERATURE, 600, 2_000);
        schedule(ObdPid.FUEL_LEVEL, 700, 5_000);
        schedule(ObdPid.VOLTAGE, 800, 5_000);
    }

    private void schedule(ObdPid pid, long initialDelayMs, long periodMs) {
        ScheduledFuture<?> task = commandExecutor.scheduleAtFixedRate(
                () -> poll(pid), initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
        pollingTasks.add(task);
    }

    private void poll(ObdPid pid) {
        if (!running.get()) {
            return;
        }
        try {
            String response = transact(pid.getCommand(), AppConfig.COMMAND_TIMEOUT);
            double value = parser.parse(pid, response);
            listener.onTelemetry(pid, value);
        } catch (ObdParseException exception) {
            listener.onLog("WARN", pid.getDisplayName() + " tidak tersedia: " + exception.getMessage());
        } catch (ObdConnectionException exception) {
            handleConnectionLoss(exception);
        } catch (RuntimeException exception) {
            listener.onLog("ERROR", "Kesalahan polling " + pid.getCommand() + ": " + exception.getMessage());
        }
    }

    private String transact(String command, Duration timeout) throws ObdConnectionException {
        listener.onLog("TX", command);
        String response = serialPortService.sendCommand(command, timeout);
        listener.onLog("RX", compactResponse(response));
        return response;
    }

    @Override
    public CompletableFuture<List<String>> readDtc() {
        if (!running.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Hubungkan ELM327 sebelum membaca DTC"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return parser.parseDtc(transact("03", Duration.ofSeconds(4)));
            } catch (ObdConnectionException | ObdParseException exception) {
                throw new CompletionException(exception);
            }
        }, commandExecutor);
    }

    @Override
    public CompletableFuture<Void> stop() {
        running.set(false);
        cancelPolling();
        return CompletableFuture.runAsync(() -> {
            serialPortService.closePort();
            listener.onLog("INFO", "Port serial ditutup");
            listener.onStatus(ConnectionStatus.DISCONNECTED, "Disconnected");
        }, commandExecutor);
    }

    private void handleConnectionLoss(ObdConnectionException exception) {
        if (!running.getAndSet(false)) {
            return;
        }
        cancelPolling();
        serialPortService.closePort();
        listener.onLog("ERROR", exception.getMessage());
        listener.onStatus(ConnectionStatus.ERROR, "Koneksi terputus");
    }

    private void cancelPolling() {
        for (ScheduledFuture<?> task : new ArrayList<>(pollingTasks)) {
            task.cancel(false);
        }
        pollingTasks.clear();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        running.set(false);
        cancelPolling();
        serialPortService.closePort();
        commandExecutor.shutdownNow();
    }

    private String compactResponse(String response) {
        return response.replace(">", "")
                .replaceAll("[\\r\\n]+", " | ")
                .replaceAll("\\s+", " ")
                .replaceAll("( \\|)+$", "")
                .trim();
    }

    private String friendlyMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Gagal terhubung ke ELM327" : message;
    }

    private record InitCommand(String command, Duration timeout, Duration pauseAfter) {
    }
}
