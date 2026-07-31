package com.example.obd2dashboard.model;

public enum ObdPid {
    ENGINE_RPM("Engine RPM", "010C", "41", "rpm", "Kecepatan putaran mesin"),
    VEHICLE_SPEED("Vehicle Speed", "010D", "41", "km/h", "Kecepatan kendaraan"),
    COOLANT_TEMPERATURE("Coolant Temperature", "0105", "41", "°C", "Suhu cairan pendingin"),
    ENGINE_LOAD("Engine Load", "0104", "41", "%", "Beban mesin terhitung"),
    THROTTLE_POSITION("Throttle Position", "0111", "41", "%", "Posisi throttle"),
    INTAKE_AIR_TEMPERATURE("Intake Air Temperature", "010F", "41", "°C", "Suhu udara masuk"),
    FUEL_LEVEL("Fuel Level", "012F", "41", "%", "Level bahan bakar"),
    MANIFOLD_PRESSURE("Manifold Pressure", "010B", "41", "kPa", "Tekanan intake manifold"),
    VOLTAGE("Adapter Voltage", "ATRV", "", "V", "Tegangan adaptor atau baterai");

    private final String displayName;
    private final String command;
    private final String expectedResponseMode;
    private final String unit;
    private final String description;

    ObdPid(String displayName, String command, String expectedResponseMode, String unit, String description) {
        this.displayName = displayName;
        this.command = command;
        this.expectedResponseMode = expectedResponseMode;
        this.unit = unit;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
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

    public String getDescription() {
        return description;
    }

    public String getPidCode() {
        return command.startsWith("01") && command.length() == 4 ? command.substring(2) : "";
    }
}
