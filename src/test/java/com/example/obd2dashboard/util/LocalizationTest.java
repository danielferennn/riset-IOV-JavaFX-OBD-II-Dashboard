package com.example.obd2dashboard.util;

import com.example.obd2dashboard.model.ObdPid;
import com.example.obd2dashboard.util.Localization.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationTest {
    private static final String KEY = "language";
    private static final Preferences PREFS = Preferences.userNodeForPackage(Localization.class);

    private String originalValue;

    @BeforeEach
    void captureState() {
        originalValue = PREFS.get(KEY, null);
    }

    @AfterEach
    void restoreState() {
        if (originalValue == null) {
            PREFS.remove(KEY);
        } else {
            PREFS.put(KEY, originalValue);
        }
        Localization.reload();
    }

    @Test
    void unsetPreferenceDefaultsToIndonesian() {
        PREFS.remove(KEY);
        Localization.reload();
        assertEquals(Language.INDONESIAN, Localization.language());
        assertEquals("id", Localization.locale().getLanguage());
    }

    @Test
    void invalidPreferenceFallsBackToIndonesian() {
        PREFS.put(KEY, "zz");
        Localization.reload();
        assertEquals(Language.INDONESIAN, Localization.language());
    }

    @Test
    void selectedLanguageIsPersistedAndReloaded() {
        Localization.setLanguage(Language.ENGLISH);
        assertEquals(Language.ENGLISH, Localization.language());

        // Simulate a fresh launch: in-memory state is reloaded from preferences.
        Localization.reload();
        assertEquals(Language.ENGLISH, Localization.language());
        assertEquals("en", Localization.locale().getLanguage());
    }

    @Test
    void textFollowsActiveLanguage() {
        Localization.setLanguage(Language.INDONESIAN);
        assertEquals("Terputus", Localization.text("status.disconnected"));

        Localization.setLanguage(Language.ENGLISH);
        assertEquals("Disconnected", Localization.text("status.disconnected"));
    }

    @Test
    void textFormatsArguments() {
        Localization.setLanguage(Language.ENGLISH);
        assertEquals("Connected - COM3", Localization.text("status.connected", "COM3"));
        assertEquals("Updated 01 Jan 2026 10:00:00",
                Localization.text("lastUpdated.prefix", "01 Jan 2026 10:00:00"));
    }

    @Test
    void numberArgumentsFollowSelectedLanguageNotJvmDefault() {
        Localization.setLanguage(Language.INDONESIAN);
        assertEquals("Port COM3 dibuka pada 38.400 baud",
                Localization.text("log.portOpened", "COM3", 38400));

        Localization.setLanguage(Language.ENGLISH);
        assertEquals("Port COM3 opened at 38,400 baud",
                Localization.text("log.portOpened", "COM3", 38400));
    }

    @Test
    void displayNamesAreStableNativeNames() {
        assertEquals("Bahasa Indonesia", Language.INDONESIAN.displayName());
        assertEquals("English", Language.ENGLISH.displayName());
    }

    @Test
    void pidDisplayNameFollowsSelectedLanguage() {
        Localization.setLanguage(Language.INDONESIAN);
        assertEquals("Putaran Mesin", ObdPid.ENGINE_RPM.getDisplayName());
        assertEquals("Tegangan Adaptor", ObdPid.VOLTAGE.getDisplayName());

        Localization.setLanguage(Language.ENGLISH);
        assertEquals("Engine RPM", ObdPid.ENGINE_RPM.getDisplayName());
        assertEquals("Adapter Voltage", ObdPid.VOLTAGE.getDisplayName());
    }

    @Test
    void pidProtocolFieldsAreNotLocalized() {
        Localization.setLanguage(Language.INDONESIAN);
        assertEquals("010C", ObdPid.ENGINE_RPM.getCommand());
        assertEquals("rpm", ObdPid.ENGINE_RPM.getUnit());
        assertEquals("ATRV", ObdPid.VOLTAGE.getCommand());
    }

    @Test
    void pidDescriptionFollowsSelectedLanguage() {
        Localization.setLanguage(Language.INDONESIAN);
        assertEquals("Kecepatan putaran mesin", ObdPid.ENGINE_RPM.getDescription());
        assertEquals("Tegangan adaptor atau baterai", ObdPid.VOLTAGE.getDescription());

        Localization.setLanguage(Language.ENGLISH);
        assertEquals("Engine speed", ObdPid.ENGINE_RPM.getDescription());
        assertEquals("Adapter or battery voltage", ObdPid.VOLTAGE.getDescription());
    }

    @Test
    void unknownKeyReturnsKeyItself() {
        Localization.setLanguage(Language.ENGLISH);
        assertEquals("does.not.exist", Localization.text("does.not.exist"));
    }

    @Test
    void bothBundlesDefineTheSameKeys() {
        ResourceBundle id = ResourceBundle.getBundle("com.example.obd2dashboard.i18n.messages",
                Language.INDONESIAN.locale());
        ResourceBundle en = ResourceBundle.getBundle("com.example.obd2dashboard.i18n.messages",
                Language.ENGLISH.locale());
        Set<String> idKeys = new TreeSet<>(id.keySet());
        Set<String> enKeys = new TreeSet<>(en.keySet());
        assertTrue(idKeys.equals(enKeys),
                "bundle key mismatch; only in id: " + difference(idKeys, enKeys)
                        + ", only in en: " + difference(enKeys, idKeys));
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> diff = new TreeSet<>(left);
        diff.removeAll(right);
        return diff;
    }
}
