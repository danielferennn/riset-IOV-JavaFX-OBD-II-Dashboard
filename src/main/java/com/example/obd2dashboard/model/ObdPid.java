package com.example.obd2dashboard.model;

import com.example.obd2dashboard.util.Localization;

public enum ObdPid {
    ENGINE_RPM("010C", "41", "rpm"),
    VEHICLE_SPEED("010D", "41", "km/h"),
    COOLANT_TEMPERATURE("0105", "41", "°C"),
    ENGINE_LOAD("0104", "41", "%"),
    THROTTLE_POSITION("0111", "41", "%"),
    INTAKE_AIR_TEMPERATURE("010F", "41", "°C"),
    FUEL_LEVEL("012F", "41", "%"),
    MANIFOLD_PRESSURE("010B", "41", "kPa"),
    VOLTAGE("ATRV", "", "V");

    private final String command;
    private final String expectedResponseMode;
    private final String unit;

    ObdPid(String command, String expectedResponseMode, String unit) {
        this.command = command;
        this.expectedResponseMode = expectedResponseMode;
        this.unit = unit;
    }

    /** Nama PID yang mengikuti bahasa antarmuka aktif. */
    public String getDisplayName() {
        return Localization.text(displayKey());
    }

    /** Kunci resource bundle untuk nama PID yang dapat dilokalkan. */
    public String displayKey() {
        return "pid." + name();
    }

    public String getCommand() {
        return command;
    }

    public String getExpectedResponseMode() {
        return expectedResponseMode;
    }

    public String getUnit() {
        return unit;
    }

    /** Deskripsi PID yang mengikuti bahasa antarmuka aktif. */
    public String getDescription() {
        return Localization.text(descriptionKey());
    }

    /** Kunci resource bundle untuk deskripsi PID yang dapat dilokalkan. */
    public String descriptionKey() {
        return "piddesc." + name();
    }

    public String getPidCode() {
        return command.startsWith("01") && command.length() == 4 ? command.substring(2) : "";
    }
}
