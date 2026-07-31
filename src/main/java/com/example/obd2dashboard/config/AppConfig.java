package com.example.obd2dashboard.config;

import java.time.Duration;
import java.util.List;

public final class AppConfig {
    public static final int DEFAULT_BAUD_RATE = 38_400;
    public static final List<Integer> BAUD_RATES = List.of(9_600, 38_400, 57_600, 115_200);
    public static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration RESET_TIMEOUT = Duration.ofSeconds(4);
    public static final int MAX_CHART_POINTS = 60;
    public static final int MAX_LOG_LINES = 500;

    private AppConfig() {
    }
}
