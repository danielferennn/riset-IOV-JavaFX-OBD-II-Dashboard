package com.example.obd2dashboard.service;

import com.example.obd2dashboard.model.ConnectionStatus;
import com.example.obd2dashboard.model.ObdPid;

public interface ObdEventListener {
    void onTelemetry(ObdPid pid, double value);

    void onStatus(ConnectionStatus status, String message);

    void onLog(String level, String message);
}
