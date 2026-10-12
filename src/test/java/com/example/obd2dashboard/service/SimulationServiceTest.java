package com.example.obd2dashboard.service;

import com.example.obd2dashboard.model.ConnectionStatus;
import com.example.obd2dashboard.model.ObdPid;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationServiceTest {
    private static final long DEADLINE_SECONDS = 5;
    private static final int TELEMETRY_PER_FRAME = ObdPid.values().length;

    @Test
    void stopWaitsForInFlightFrameThenEmitsDisconnectLast() throws Exception {
        RecordingListener listener = new RecordingListener(true);
        SimulationService service = new SimulationService(listener);
        try {
            service.start();

            // The first frame blocks inside its first telemetry callback on the scheduler thread.
            assertTrue(listener.firstTelemetryArrived.await(DEADLINE_SECONDS, TimeUnit.SECONDS),
                    "first frame telemetry callback never arrived");

            CompletableFuture<Void> stopFuture = service.stop();
            assertFalse(service.isRunning(), "running flag must clear immediately on stop");
            assertFalse(stopFuture.isDone(),
                    "stop future must stay incomplete while a frame callback is in flight");

            listener.releaseFirstTelemetry.countDown();
            stopFuture.get(DEADLINE_SECONDS, TimeUnit.SECONDS);

            // Only the blocked frame ever ran, so exactly one frame worth of telemetry was produced.
            assertEquals(TELEMETRY_PER_FRAME, listener.telemetryOrder.size(),
                    "no frame task may run after the stop future completes");

            int disconnectedIndex = listener.events.indexOf("status:" + ConnectionStatus.DISCONNECTED.name());
            assertTrue(disconnectedIndex >= 0, "stop barrier must emit DISCONNECTED");
            assertEquals(1, listener.countStatus(ConnectionStatus.DISCONNECTED),
                    "DISCONNECTED must be emitted exactly once");
            assertEquals(listener.events.size() - 1, disconnectedIndex,
                    "DISCONNECTED must be the last event emitted by the stop barrier");
            for (int i = disconnectedIndex + 1; i < listener.events.size(); i++) {
                assertFalse(listener.events.get(i).startsWith("telemetry:"),
                        "telemetry emitted after DISCONNECTED: " + listener.events.get(i));
            }
        } finally {
            listener.releaseFirstTelemetry.countDown();
            service.close();
        }
    }

    @Test
    void restartAfterCompletedStopRunsNewSessionAndOldBarrierDoesNotStopIt() throws Exception {
        RecordingListener listener = new RecordingListener(true);
        SimulationService service = new SimulationService(listener);
        try {
            service.start();
            assertTrue(listener.firstTelemetryArrived.await(DEADLINE_SECONDS, TimeUnit.SECONDS),
                    "first frame telemetry callback never arrived");

            CompletableFuture<Void> firstStop = service.stop();
            assertFalse(firstStop.isDone(), "first stop future must wait for the in-flight frame");
            listener.releaseFirstTelemetry.countDown();
            firstStop.get(DEADLINE_SECONDS, TimeUnit.SECONDS);
            assertFalse(service.isRunning(), "service must be idle after first stop completes");

            // Restart: the already-settled barrier must not suppress the fresh session.
            CountDownLatch newSessionFrame = listener.armNextTelemetry();
            service.start();
            assertTrue(service.isRunning(), "restart must run a new session");
            assertTrue(newSessionFrame.await(DEADLINE_SECONDS, TimeUnit.SECONDS),
                    "new session must produce telemetry");
            assertTrue(service.isRunning(), "new session must keep running until stopped");

            CompletableFuture<Void> secondStop = service.stop();
            assertNotSame(firstStop, secondStop, "a new stop must create a new barrier");
            secondStop.get(DEADLINE_SECONDS, TimeUnit.SECONDS);

            assertEquals(2, listener.countStatus(ConnectionStatus.DISCONNECTED),
                    "each session stop must emit its own DISCONNECTED");
        } finally {
            listener.releaseFirstTelemetry.countDown();
            service.close();
        }
    }

    private static final class RecordingListener implements ObdEventListener {
        private final List<String> events = new CopyOnWriteArrayList<>();
        private final List<ObdPid> telemetryOrder = new CopyOnWriteArrayList<>();
        private final AtomicBoolean firstTelemetrySeen = new AtomicBoolean();
        private final AtomicReference<CountDownLatch> nextTelemetryLatch = new AtomicReference<>();
        private final CountDownLatch firstTelemetryArrived = new CountDownLatch(1);
        private final CountDownLatch releaseFirstTelemetry = new CountDownLatch(1);
        private final boolean blockFirstTelemetry;

        RecordingListener(boolean blockFirstTelemetry) {
            this.blockFirstTelemetry = blockFirstTelemetry;
        }

        CountDownLatch armNextTelemetry() {
            CountDownLatch latch = new CountDownLatch(1);
            nextTelemetryLatch.set(latch);
            return latch;
        }

        int countStatus(ConnectionStatus status) {
            String needle = "status:" + status.name();
            int count = 0;
            for (String event : events) {
                if (needle.equals(event)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public void onTelemetry(ObdPid pid, double value) {
            if (firstTelemetrySeen.compareAndSet(false, true)) {
                firstTelemetryArrived.countDown();
                if (blockFirstTelemetry) {
                    try {
                        releaseFirstTelemetry.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            telemetryOrder.add(pid);
            events.add("telemetry:" + pid.name());

            CountDownLatch armed = nextTelemetryLatch.getAndSet(null);
            if (armed != null) {
                armed.countDown();
            }
        }

        @Override
        public void onStatus(ConnectionStatus status, String message) {
            events.add("status:" + status.name());
        }

        @Override
        public void onLog(String level, String message) {
            events.add("log:" + level);
        }
    }
}
