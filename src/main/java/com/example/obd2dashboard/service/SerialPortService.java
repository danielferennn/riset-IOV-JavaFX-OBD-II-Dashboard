package com.example.obd2dashboard.service;

import com.example.obd2dashboard.exception.ObdConnectionException;
import com.fazecast.jSerialComm.SerialPort;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

public final class SerialPortService implements AutoCloseable {
    private final Object commandLock = new Object();
    private volatile SerialPort serialPort;

    public List<String> listAvailablePorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(SerialPort::getSystemPortName)
                .sorted()
                .toList();
    }

    public void openPort(String portName, int baudRate) throws ObdConnectionException {
        synchronized (commandLock) {
            if (isOpen()) {
                throw new ObdConnectionException("Port serial sudah terbuka");
            }
            if (portName == null || portName.isBlank()) {
                throw new ObdConnectionException("Nama port serial belum dipilih");
            }

            SerialPort candidate = SerialPort.getCommPort(portName);
            candidate.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
            candidate.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
            if (!candidate.openPort()) {
                throw new ObdConnectionException(
                        "Gagal membuka " + portName + ". Port mungkin sedang digunakan aplikasi lain.");
            }
            serialPort = candidate;
        }
    }

    public void closePort() {
        synchronized (commandLock) {
            SerialPort current = serialPort;
            serialPort = null;
            if (current != null && current.isOpen()) {
                current.closePort();
            }
        }
    }

    public boolean isOpen() {
        SerialPort current = serialPort;
        return current != null && current.isOpen();
    }

    public String sendCommand(String command, Duration timeout) throws ObdConnectionException {
        synchronized (commandLock) {
            SerialPort current = requireOpenPort();
            discardPendingInput(current);

            byte[] payload = (command.trim() + "\r").getBytes(StandardCharsets.US_ASCII);
            int written = current.writeBytes(payload, payload.length);
            if (written != payload.length) {
                throw new ObdConnectionException("Gagal menulis command " + command + " ke port serial");
            }
            return readUntilPrompt(timeout);
        }
    }

    public String readUntilPrompt(Duration timeout) throws ObdConnectionException {
        SerialPort current = requireOpenPort();
        long deadline = System.nanoTime() + timeout.toNanos();
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];

        while (System.nanoTime() < deadline) {
            if (!current.isOpen()) {
                throw new ObdConnectionException("Koneksi serial terputus");
            }

            int available = current.bytesAvailable();
            if (available < 0) {
                throw new ObdConnectionException("Gagal membaca status port serial");
            }
            if (available == 0) {
                LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
                continue;
            }

            int read = current.readBytes(buffer, Math.min(buffer.length, available));
            if (read < 0) {
                throw new ObdConnectionException("Terjadi kesalahan saat membaca port serial");
            }
            response.write(buffer, 0, read);
            boolean promptFound = false;
            for (int index = 0; index < read; index++) {
                if (buffer[index] == '>') {
                    promptFound = true;
                    break;
                }
            }
            if (promptFound) {
                return response.toString(StandardCharsets.US_ASCII);
            }
        }
        throw new ObdConnectionException("Timeout menunggu prompt ELM327");
    }

    private SerialPort requireOpenPort() throws ObdConnectionException {
        SerialPort current = serialPort;
        if (current == null || !current.isOpen()) {
            throw new ObdConnectionException("Port serial belum terhubung");
        }
        return current;
    }

    private void discardPendingInput(SerialPort port) {
        byte[] discard = new byte[256];
        int available;
        while ((available = port.bytesAvailable()) > 0) {
            port.readBytes(discard, Math.min(discard.length, available));
        }
    }

    @Override
    public void close() {
        closePort();
    }
}
