package com.example.obd2dashboard.parser;

import com.example.obd2dashboard.exception.ObdParseException;
import com.example.obd2dashboard.model.ObdPid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObdResponseParserTest {
    private ObdResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new ObdResponseParser();
    }

    @Test
    void parsesRpm() throws Exception {
        assertEquals(1726.0, parser.parse(ObdPid.ENGINE_RPM, "41 0C 1A F8"));
    }

    @Test
    void parsesSpeed() throws Exception {
        assertEquals(40.0, parser.parse(ObdPid.VEHICLE_SPEED, "41 0D 28"));
    }

    @Test
    void parsesCoolantTemperature() throws Exception {
        assertEquals(80.0, parser.parse(ObdPid.COOLANT_TEMPERATURE, "41 05 78"));
    }

    @Test
    void parsesEngineLoad() throws Exception {
        assertEquals(50.196, parser.parse(ObdPid.ENGINE_LOAD, "41 04 80"), 0.001);
    }

    @Test
    void parsesThrottle() throws Exception {
        assertEquals(25.098, parser.parse(ObdPid.THROTTLE_POSITION, "41 11 40"), 0.001);
    }

    @Test
    void parsesFuelLevel() throws Exception {
        assertEquals(75.294, parser.parse(ObdPid.FUEL_LEVEL, "41 2F C0"), 0.001);
    }

    @Test
    void parsesManifoldPressure() throws Exception {
        assertEquals(100.0, parser.parse(ObdPid.MANIFOLD_PRESSURE, "41 0B 64"));
    }

    @Test
    void ignoresEchoCommand() throws Exception {
        assertEquals(40.0, parser.parse(ObdPid.VEHICLE_SPEED, "010D\r41 0D 28\r>"));
    }

    @Test
    void ignoresPromptAndCanHeader() throws Exception {
        assertEquals(1726.0, parser.parse(ObdPid.ENGINE_RPM, "7E8 04 41 0C 1A F8\r>"));
    }

    @Test
    void rejectsNoData() {
        assertThrows(ObdParseException.class, () -> parser.parse(ObdPid.ENGINE_RPM, "NO DATA\r>"));
    }

    @Test
    void rejectsShortResponse() {
        assertThrows(ObdParseException.class, () -> parser.parse(ObdPid.ENGINE_RPM, "41 0C 1A"));
    }

    @Test
    void parsesVoltage() throws Exception {
        assertEquals(13.8, parser.parseVoltage("ATRV\r13.8V\r>"));
    }

    @Test
    void parsesMultipleDtcs() throws Exception {
        assertEquals(List.of("P0300", "P0171", "U0100"), parser.parseDtc("43 03 00 01 71 C1 00\r>"));
    }

    @Test
    void ignoresZeroDtcPairs() throws Exception {
        assertEquals(List.of("P0300"), parser.parseDtc("43 03 00 00 00\r>"));
    }
}
