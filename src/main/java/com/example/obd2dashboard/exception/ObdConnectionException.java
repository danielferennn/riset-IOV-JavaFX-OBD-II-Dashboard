package com.example.obd2dashboard.exception;

public class ObdConnectionException extends Exception {
    public ObdConnectionException(String message) {
        super(message);
    }

    public ObdConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
