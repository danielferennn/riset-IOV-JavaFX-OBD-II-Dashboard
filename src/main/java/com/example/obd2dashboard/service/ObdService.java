package com.example.obd2dashboard.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ObdService extends AutoCloseable {
    boolean isRunning();

    CompletableFuture<Void> stop();

    CompletableFuture<List<String>> readDtc();

    @Override
    void close();
}
