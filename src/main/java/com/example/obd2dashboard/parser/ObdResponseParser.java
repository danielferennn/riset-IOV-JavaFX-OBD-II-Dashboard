package com.example.obd2dashboard.parser;

import com.example.obd2dashboard.exception.ObdParseException;
import com.example.obd2dashboard.model.ObdPid;
import com.example.obd2dashboard.util.HexUtils;
import com.example.obd2dashboard.util.Localization;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ObdResponseParser {
    private static final Pattern VOLTAGE_PATTERN = Pattern.compile("(?i)(\\d{1,2}(?:\\.\\d+)?)\\s*V");

    public double parse(ObdPid pid, String rawResponse) throws ObdParseException {
        if (pid == ObdPid.VOLTAGE) {
            return parseVoltage(rawResponse);
        }

        String payload = findMode01Payload(pid, rawResponse);
        List<String> bytes = splitBytes(payload);
        return switch (pid) {
            case ENGINE_RPM -> {
                requireBytes(pid, bytes, 2);
                yield ((HexUtils.parseByte(bytes.get(0)) * 256) + HexUtils.parseByte(bytes.get(1))) / 4.0;
            }
            case VEHICLE_SPEED -> singleByte(pid, bytes);
            case COOLANT_TEMPERATURE, INTAKE_AIR_TEMPERATURE -> singleByte(pid, bytes) - 40.0;
            case ENGINE_LOAD, THROTTLE_POSITION, FUEL_LEVEL -> singleByte(pid, bytes) * 100.0 / 255.0;
            case MANIFOLD_PRESSURE -> singleByte(pid, bytes);
            case VOLTAGE -> throw new IllegalStateException(Localization.text("parser.voltageOrder"));
        };
    }

    public double parseVoltage(String rawResponse) throws ObdParseException {
        String cleaned = validateAndClean(rawResponse, "ATRV");
        Matcher matcher = VOLTAGE_PATTERN.matcher(cleaned);
        if (!matcher.find()) {
            throw new ObdParseException(Localization.text("parser.voltageInvalid", summarize(rawResponse)));
        }
        return Double.parseDouble(matcher.group(1));
    }

    public List<String> parseDtc(String rawResponse) throws ObdParseException {
        String cleaned = validateAndClean(rawResponse, "03");
        Set<String> result = new LinkedHashSet<>();
        boolean sawValidResponse = false;

        for (String line : cleaned.split("\\R")) {
            String compact = line.replaceAll("(?i)[^0-9A-F]", "").toUpperCase(Locale.ROOT);
            int marker = compact.indexOf("43");
            if (marker < 0) {
                continue;
            }
            String payload = compact.substring(marker + 2);
            List<String> bytes = splitBytes(payload);
            if (bytes.isEmpty()) {
                throw new ObdParseException(Localization.text("parser.mode03Invalid", summarize(rawResponse)));
            }
            if (isAllZero(bytes)) {
                sawValidResponse = true;
                continue;
            }
            if (bytes.size() < 2 || bytes.size() % 2 != 0) {
                throw new ObdParseException(Localization.text("parser.mode03Invalid", summarize(rawResponse)));
            }
            sawValidResponse = true;
            for (int index = 0; index + 1 < bytes.size(); index += 2) {
                int first = HexUtils.parseByte(bytes.get(index));
                int second = HexUtils.parseByte(bytes.get(index + 1));
                if (first == 0 && second == 0) {
                    continue;
                }
                result.add(decodeDtc(first, second));
            }
        }

        if (!sawValidResponse) {
            throw new ObdParseException(Localization.text("parser.mode03Invalid", summarize(rawResponse)));
        }
        return List.copyOf(result);
    }

    private String findMode01Payload(ObdPid pid, String rawResponse) throws ObdParseException {
        String cleaned = validateAndClean(rawResponse, pid.getCommand());
        String marker = pid.getExpectedResponseMode() + pid.getPidCode();

        for (String line : cleaned.split("\\R")) {
            String compact = line.replaceAll("(?i)[^0-9A-F]", "").toUpperCase(Locale.ROOT);
            int markerIndex = compact.indexOf(marker);
            if (markerIndex >= 0) {
                return compact.substring(markerIndex + marker.length());
            }
        }
        throw new ObdParseException(Localization.text("parser.markerMissing", marker, summarize(rawResponse)));
    }

    private String validateAndClean(String rawResponse, String echoedCommand) throws ObdParseException {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new ObdParseException(Localization.text("parser.emptyResponse"));
        }

        String upper = rawResponse.toUpperCase(Locale.ROOT);
        for (String error : List.of("NO DATA", "STOPPED", "UNABLE TO CONNECT", "ERROR", "?")) {
            if (upper.contains(error)) {
                throw new ObdParseException(Localization.text("parser.elmReported", error));
            }
        }

        String withoutPrompt = rawResponse.replace(">", "");
        List<String> retainedLines = new ArrayList<>();
        for (String line : withoutPrompt.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()
                    || trimmed.equalsIgnoreCase(echoedCommand)
                    || trimmed.toUpperCase(Locale.ROOT).startsWith("SEARCHING")) {
                continue;
            }
            retainedLines.add(trimmed);
        }
        if (retainedLines.isEmpty()) {
            throw new ObdParseException(Localization.text("parser.noPayload"));
        }
        return String.join(System.lineSeparator(), retainedLines);
    }

    private List<String> splitBytes(String compactPayload) throws ObdParseException {
        String compact = compactPayload.replaceAll("(?i)[^0-9A-F]", "").toUpperCase(Locale.ROOT);
        if (compact.length() % 2 != 0) {
            throw new ObdParseException(Localization.text("parser.oddHex"));
        }
        List<String> bytes = new ArrayList<>();
        for (int index = 0; index < compact.length(); index += 2) {
            bytes.add(compact.substring(index, index + 2));
        }
        return bytes;
    }

    private boolean isAllZero(List<String> bytes) throws ObdParseException {
        for (String value : bytes) {
            if (HexUtils.parseByte(value) != 0) {
                return false;
            }
        }
        return true;
    }

    private double singleByte(ObdPid pid, List<String> bytes) throws ObdParseException {
        requireBytes(pid, bytes, 1);
        return HexUtils.parseByte(bytes.get(0));
    }

    private void requireBytes(ObdPid pid, List<String> bytes, int count) throws ObdParseException {
        if (bytes.size() < count) {
            throw new ObdParseException(Localization.text("parser.responseTooShort", pid.getCommand()));
        }
    }

    private String decodeDtc(int first, int second) {
        char category = switch ((first >> 6) & 0x03) {
            case 0 -> 'P';
            case 1 -> 'C';
            case 2 -> 'B';
            default -> 'U';
        };
        return String.format(Locale.US, "%c%01X%02X", category, (first >> 4) & 0x03, ((first & 0x0F) << 8) | second);
    }

    private String summarize(String rawResponse) {
        if (rawResponse == null) {
            return "null";
        }
        return rawResponse.replaceAll("\\s+", " ").trim();
    }
}
