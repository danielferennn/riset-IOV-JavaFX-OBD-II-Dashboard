package com.example.obd2dashboard.service;

import com.example.obd2dashboard.model.ConnectionStatus;
import com.example.obd2dashboard.model.ObdPid;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SimulationService implements ObdService {
    private final ObdEventListener listener;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean();
    private ScheduledFuture<?> updateTask;
    private double phase;
    private double fuelLevel = 78.0;

    public SimulationService(ObdEventListener listener) {
        this.listener = listener;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "obd-simulation-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        listener.onLog("INFO", "Mode simulasi diaktifkan");
        listener.onStatus(ConnectionStatus.SIMULATION, "Simulation");
        updateTask = scheduler.scheduleAtFixedRate(this::generateFrame, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void generateFrame() {
        if (!running.get()) {
            return;
        }
        phase += 0.10;
        double speed = clamp(45 + 42 * Math.sin(phase * 0.45) + 12 * Math.sin(phase * 0.12), 0, 140);
        double throttle = clamp(28 + 20 * Math.sin(phase * 0.72), 5, 80);
        double rpm = clamp(780 + speed * 24 + throttle * 15 + 110 * Math.sin(phase), 700, 4500);
        double coolant = clamp(88 + 5 * Math.sin(phase * 0.08), 75, 105);
        double load = clamp(22 + throttle * 0.72 + 7 * Math.sin(phase * 0.31), 10, 90);
        double intakeTemperature = clamp(31 + 6 * Math.sin(phase * 0.06), 20, 55);
        double manifoldPressure = clamp(24 + throttle * 0.88, 20, 100);
        double voltage = clamp(13.8 + 0.25 * Math.sin(phase * 0.22), 12.2, 14.6);
        fuelLevel = Math.max(0, fuelLevel - 0.002);

        listener.onTelemetry(ObdPid.ENGINE_RPM, rpm);
        listener.onTelemetry(ObdPid.VEHICLE_SPEED, speed);
        listener.onTelemetry(ObdPid.COOLANT_TEMPERATURE, coolant);
        listener.onTelemetry(ObdPid.ENGINE_LOAD, load);
        listener.onTelemetry(ObdPid.THROTTLE_POSITION, throttle);
        listener.onTelemetry(ObdPid.INTAKE_AIR_TEMPERATURE, intakeTemperature);
        listener.onTelemetry(ObdPid.FUEL_LEVEL, fuelLevel);
        listener.onTelemetry(ObdPid.MANIFOLD_PRESSURE, manifoldPressure);
        listener.onTelemetry(ObdPid.VOLTAGE, voltage);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public CompletableFuture<Void> stop() {
        if (running.getAndSet(false)) {
            if (updateTask != null) {
                updateTask.cancel(false);
            }
            listener.onLog("INFO", "Mode simulasi dihentikan");
            listener.onStatus(ConnectionStatus.DISCONNECTED, "Disconnected");
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<String>> readDtc() {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        running.set(false);
        if (updateTask != null) {
            updateTask.cancel(false);
        }
        scheduler.shutdownNow();
    }
}
