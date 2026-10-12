package com.example.obd2dashboard.service;

import com.example.obd2dashboard.config.AppConfig;
import com.example.obd2dashboard.exception.ObdConnectionException;
import com.example.obd2dashboard.exception.ObdParseException;
import com.example.obd2dashboard.model.ConnectionStatus;
import com.example.obd2dashboard.model.ObdPid;
import com.example.obd2dashboard.parser.ObdResponseParser;
import com.example.obd2dashboard.util.Localization;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public final class Elm327Service implements ObdService {
    private enum State { IDLE, CONNECTING, RUNNING, STOPPING, CLOSED }

    private final SerialPortService serialPortService;
    private final ObdResponseParser parser;
    private final ObdEventListener listener;
    private final ScheduledExecutorService commandExecutor;
    private final List<ScheduledFuture<?>> pollingTasks = new ArrayList<>();
    private final Object lifecycleLock = new Object();
    private State state = State.IDLE;
    private long sessionId;
    private CompletableFuture<Void> stopFuture;

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
        synchronized (lifecycleLock) {
            if (state != State.IDLE) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException(Localization.text("elmerror.alreadyActive")));
            }

            state = State.CONNECTING;
            long session = ++sessionId;
            CompletableFuture<Void> result = new CompletableFuture<>();
            try {
                listener.onStatus(ConnectionStatus.CONNECTING, Localization.text("status.connecting", portName));
                commandExecutor.execute(() -> connectWorker(portName, baudRate, session, result));
            } catch (RuntimeException exception) {
                state = State.IDLE;
                result.completeExceptionally(exception);
            }
            return result;
        }
    }

    private void connectWorker(String portName, int baudRate, long session, CompletableFuture<Void> result) {
        try {
            ensureConnecting(session);
            serialPortService.openPort(portName, baudRate);
            ensureConnecting(session);
            listener.onLog("INFO", Localization.text("log.portOpened", portName, baudRate));
            initializeElm327(session);

            synchronized (lifecycleLock) {
                ensureConnectingLocked(session);
                schedulePolling(session);
                state = State.RUNNING;
                listener.onStatus(ConnectionStatus.CONNECTED, Localization.text("status.connected", portName));
            }
            result.complete(null);
        } catch (Exception exception) {
            try {
                serialPortService.closePort();
            } catch (RuntimeException closeException) {
                exception.addSuppressed(closeException);
            }

            boolean invalidated;
            synchronized (lifecycleLock) {
                invalidated = !((state == State.CONNECTING || state == State.RUNNING) && sessionId == session);
                if (!invalidated) {
                    cancelPolling();
                    state = State.IDLE;
                    listener.onStatus(ConnectionStatus.ERROR, friendlyMessage(exception));
                    listener.onLog("ERROR", friendlyMessage(exception));
                }
            }
            if (invalidated) {
                result.completeExceptionally(new CancellationException(Localization.text("elmerror.connectCancelled")));
            } else {
                result.completeExceptionally(exception);
            }
        }
    }

    private void initializeElm327(long session) throws ObdConnectionException {
        List<InitCommand> commands = List.of(
                new InitCommand("ATZ", AppConfig.RESET_TIMEOUT, Duration.ofMillis(700)),
                new InitCommand("ATE0", AppConfig.COMMAND_TIMEOUT, Duration.ZERO),
                new InitCommand("ATL0", AppConfig.COMMAND_TIMEOUT, Duration.ZERO),
                new InitCommand("ATS0", AppConfig.COMMAND_TIMEOUT, Duration.ZERO),
                new InitCommand("ATH0", AppConfig.COMMAND_TIMEOUT, Duration.ZERO),
                new InitCommand("ATSP0", AppConfig.COMMAND_TIMEOUT, Duration.ZERO)
        );
        for (InitCommand initCommand : commands) {
            ensureConnecting(session);
            transact(initCommand.command(), initCommand.timeout());
            ensureConnecting(session);
            if (!initCommand.pauseAfter().isZero()) {
                LockSupport.parkNanos(initCommand.pauseAfter().toNanos());
                ensureConnecting(session);
            }
        }
        ensureConnecting(session);
    }

    private void schedulePolling(long session) {
        schedule(ObdPid.ENGINE_RPM, 0, 500, session);
        schedule(ObdPid.VEHICLE_SPEED, 100, 500, session);
        schedule(ObdPid.THROTTLE_POSITION, 200, 1_000, session);
        schedule(ObdPid.ENGINE_LOAD, 300, 1_000, session);
        schedule(ObdPid.MANIFOLD_PRESSURE, 400, 1_000, session);
        schedule(ObdPid.COOLANT_TEMPERATURE, 500, 2_000, session);
        schedule(ObdPid.INTAKE_AIR_TEMPERATURE, 600, 2_000, session);
        schedule(ObdPid.FUEL_LEVEL, 700, 5_000, session);
        schedule(ObdPid.VOLTAGE, 800, 5_000, session);
    }

    private void schedule(ObdPid pid, long initialDelayMs, long periodMs, long session) {
        ScheduledFuture<?> task = commandExecutor.scheduleAtFixedRate(
                () -> poll(pid, session), initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
        pollingTasks.add(task);
    }

    private void poll(ObdPid pid, long session) {
        if (!isRunningSession(session)) {
            return;
        }
        try {
            String response = transact(pid.getCommand(), AppConfig.COMMAND_TIMEOUT);
            double value = parser.parse(pid, response);
            if (isRunningSession(session)) {
                listener.onTelemetry(pid, value);
            }
        } catch (ObdParseException exception) {
            if (isRunningSession(session)) {
                listener.onLog("WARN", Localization.text("log.pidUnavailable",
                        Localization.text(pid.displayKey()), exception.getMessage()));
            }
        } catch (ObdConnectionException exception) {
            handleConnectionLoss(exception, session);
        } catch (RuntimeException exception) {
            if (isRunningSession(session)) {
                listener.onLog("ERROR", Localization.text("log.pollingError", pid.getCommand(), exception.getMessage()));
            }
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
        synchronized (lifecycleLock) {
            if (state != State.RUNNING) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException(Localization.text("elmerror.dtcBeforeConnect")));
            }

            long session = sessionId;
            CompletableFuture<List<String>> result = new CompletableFuture<>();
            try {
                commandExecutor.execute(() -> {
                    try {
                        ensureRunningSession(session);
                        List<String> codes = parser.parseDtc(transact("03", Duration.ofSeconds(4)));
                        ensureRunningSession(session);
                        result.complete(codes);
                    } catch (Exception exception) {
                        result.completeExceptionally(exception);
                    }
                });
            } catch (RejectedExecutionException exception) {
                result.completeExceptionally(exception);
            }
            return result;
        }
    }

    @Override
    public CompletableFuture<Void> stop() {
        synchronized (lifecycleLock) {
            if (state == State.CLOSED) {
                return CompletableFuture.completedFuture(null);
            }
            if (state == State.STOPPING) {
                return stopFuture;
            }

            state = State.STOPPING;
            ++sessionId;
            cancelPolling();
            stopFuture = new CompletableFuture<>();
            CompletableFuture<Void> result = stopFuture;
            try {
                commandExecutor.execute(() -> stopWorker(result));
            } catch (RejectedExecutionException exception) {
                state = State.IDLE;
                result.completeExceptionally(exception);
            }
            return result;
        }
    }

    private void stopWorker(CompletableFuture<Void> result) {
        RuntimeException failure = null;
        synchronized (lifecycleLock) {
            cancelPolling();
        }
        try {
            serialPortService.closePort();
        } catch (RuntimeException exception) {
            failure = exception;
        }

        synchronized (lifecycleLock) {
            if (state == State.CLOSED) {
                result.completeExceptionally(new CancellationException(Localization.text("elmerror.serviceClosed")));
                return;
            }
            if (state != State.STOPPING || stopFuture != result) {
                result.completeExceptionally(new CancellationException(Localization.text("elmerror.stopSessionEnded")));
                return;
            }

            state = State.IDLE;
            if (failure == null) {
                listener.onLog("INFO", Localization.text("log.serialClosed"));
                listener.onStatus(ConnectionStatus.DISCONNECTED, Localization.text("status.disconnected"));
                result.complete(null);
            } else {
                listener.onStatus(ConnectionStatus.ERROR, friendlyMessage(failure));
                listener.onLog("ERROR", friendlyMessage(failure));
                result.completeExceptionally(failure);
            }
        }
    }

    private void handleConnectionLoss(ObdConnectionException exception, long session) {
        long cleanupSession;
        synchronized (lifecycleLock) {
            if (state != State.RUNNING || sessionId != session) {
                return;
            }
            cleanupSession = ++sessionId;
            cancelPolling();
        }

        try {
            serialPortService.closePort();
        } catch (RuntimeException closeException) {
            exception.addSuppressed(closeException);
        }

        synchronized (lifecycleLock) {
            if (state == State.RUNNING && sessionId == cleanupSession) {
                state = State.IDLE;
                listener.onLog("ERROR", friendlyMessage(exception));
                listener.onStatus(ConnectionStatus.ERROR, Localization.text("status.connectionLost"));
            }
        }
    }

    private void cancelPolling() {
        for (ScheduledFuture<?> task : pollingTasks) {
            task.cancel(false);
        }
        pollingTasks.clear();
    }

    private void ensureConnecting(long session) {
        synchronized (lifecycleLock) {
            ensureConnectingLocked(session);
        }
    }

    private void ensureConnectingLocked(long session) {
        if (state != State.CONNECTING || sessionId != session) {
            throw new CancellationException(Localization.text("elmerror.connectCancelled"));
        }
    }

    private void ensureRunningSession(long session) {
        synchronized (lifecycleLock) {
            if (state != State.RUNNING || sessionId != session) {
                throw new CancellationException(Localization.text("elmerror.sessionEnded"));
            }
        }
    }

    private boolean isRunningSession(long session) {
        synchronized (lifecycleLock) {
            return state == State.RUNNING && sessionId == session;
        }
    }

    @Override
    public boolean isRunning() {
        synchronized (lifecycleLock) {
            return state == State.RUNNING;
        }
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (state == State.CLOSED) {
                return;
            }
            state = State.CLOSED;
            ++sessionId;
            cancelPolling();
            commandExecutor.shutdown();
        }
        serialPortService.closePort();
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
        return message == null || message.isBlank()
                ? Localization.text("elmerror.connectFailed") : message;
    }

    private record InitCommand(String command, Duration timeout, Duration pauseAfter) {
    }
}
