package com.example.obd2dashboard.util;

import com.example.obd2dashboard.exception.ObdParseException;

public final class HexUtils {
    private HexUtils() {
    }

    public static int parseByte(String value) throws ObdParseException {
        if (value == null || !value.matches("(?i)[0-9a-f]{2}")) {
            throw new ObdParseException("Byte hexadecimal tidak valid: " + value);
        }
        return Integer.parseInt(value, 16);
    }
}
