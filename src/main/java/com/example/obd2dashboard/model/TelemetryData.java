package com.example.obd2dashboard.model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.time.LocalDateTime;

public final class TelemetryData {
    private final ObjectProperty<Double> rpm = new SimpleObjectProperty<>();
    private final ObjectProperty<Double> speedKmh = new SimpleObjectProperty<>();
    private final ObjectProperty<Double> coolantTemperatureC = new SimpleObjectProperty<>();
    private final ObjectProperty<Double> engineLoadPercent = new SimpleObjectProperty<>();
    private final ObjectProperty<Double> throttlePositionPercent = new SimpleObjectProperty<>();
    private final ObjectProperty<Double> intakeAirTemperatureC = new SimpleObjectProperty<>();
    private final ObjectProperty<Double> fuelLevelPercent = new SimpleObjectProperty<>();
    private final ObjectProperty<Double> manifoldPressureKpa = new SimpleObjectProperty<>();
    private final ObjectProperty<Double> voltage = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> lastUpdated = new SimpleObjectProperty<>();
    private final ObjectProperty<ConnectionStatus> connectionStatus =
            new SimpleObjectProperty<>(ConnectionStatus.DISCONNECTED);

    public Double getRpm() { return rpm.get(); }
    public void setRpm(Double value) { rpm.set(value); }
    public ObjectProperty<Double> rpmProperty() { return rpm; }

    public Double getSpeedKmh() { return speedKmh.get(); }
    public void setSpeedKmh(Double value) { speedKmh.set(value); }
    public ObjectProperty<Double> speedKmhProperty() { return speedKmh; }

    public Double getCoolantTemperatureC() { return coolantTemperatureC.get(); }
    public void setCoolantTemperatureC(Double value) { coolantTemperatureC.set(value); }
    public ObjectProperty<Double> coolantTemperatureCProperty() { return coolantTemperatureC; }

    public Double getEngineLoadPercent() { return engineLoadPercent.get(); }
    public void setEngineLoadPercent(Double value) { engineLoadPercent.set(value); }
    public ObjectProperty<Double> engineLoadPercentProperty() { return engineLoadPercent; }

    public Double getThrottlePositionPercent() { return throttlePositionPercent.get(); }
    public void setThrottlePositionPercent(Double value) { throttlePositionPercent.set(value); }
    public ObjectProperty<Double> throttlePositionPercentProperty() { return throttlePositionPercent; }

    public Double getIntakeAirTemperatureC() { return intakeAirTemperatureC.get(); }
    public void setIntakeAirTemperatureC(Double value) { intakeAirTemperatureC.set(value); }
    public ObjectProperty<Double> intakeAirTemperatureCProperty() { return intakeAirTemperatureC; }

    public Double getFuelLevelPercent() { return fuelLevelPercent.get(); }
    public void setFuelLevelPercent(Double value) { fuelLevelPercent.set(value); }
    public ObjectProperty<Double> fuelLevelPercentProperty() { return fuelLevelPercent; }

    public Double getManifoldPressureKpa() { return manifoldPressureKpa.get(); }
    public void setManifoldPressureKpa(Double value) { manifoldPressureKpa.set(value); }
    public ObjectProperty<Double> manifoldPressureKpaProperty() { return manifoldPressureKpa; }

    public Double getVoltage() { return voltage.get(); }
    public void setVoltage(Double value) { voltage.set(value); }
    public ObjectProperty<Double> voltageProperty() { return voltage; }

    public LocalDateTime getLastUpdated() { return lastUpdated.get(); }
    public void setLastUpdated(LocalDateTime value) { lastUpdated.set(value); }
    public ObjectProperty<LocalDateTime> lastUpdatedProperty() { return lastUpdated; }

    public ConnectionStatus getConnectionStatus() { return connectionStatus.get(); }
    public void setConnectionStatus(ConnectionStatus value) { connectionStatus.set(value); }
    public ObjectProperty<ConnectionStatus> connectionStatusProperty() { return connectionStatus; }

    public void clearSensorValues() {
        setRpm(null);
        setSpeedKmh(null);
        setCoolantTemperatureC(null);
        setEngineLoadPercent(null);
        setThrottlePositionPercent(null);
        setIntakeAirTemperatureC(null);
        setFuelLevelPercent(null);
        setManifoldPressureKpa(null);
        setVoltage(null);
        setLastUpdated(null);
    }
}
