package com.example.obd2dashboard.util;

import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Pusat lokalisasi aplikasi. Menyimpan bahasa aktif, memuat resource bundle, dan
 * mempertahankan pilihan pengguna lewat {@link Preferences}. Bahasa default adalah
 * Bahasa Indonesia bila preferensi belum diset atau tidak dikenali.
 */
public final class Localization {
    /** Bahasa antarmuka yang didukung aplikasi. */
    public enum Language {
        INDONESIAN("Bahasa Indonesia", "id"),
        ENGLISH("English", "en");

        private final String displayName;
        private final String code;
        private final Locale locale;

        Language(String displayName, String code) {
            this.displayName = displayName;
            this.code = code;
            this.locale = Locale.forLanguageTag(code);
        }

        /** Nama bahasa asli yang stabil, tidak berubah mengikuti bahasa aktif. */
        public String displayName() {
            return displayName;
        }

        /** Kode ISO singkat untuk disimpan di preferensi. */
        public String code() {
            return code;
        }

        public Locale locale() {
            return locale;
        }

        static Language fromCode(String code) {
            if (code != null) {
                String trimmed = code.trim();
                for (Language language : values()) {
                    if (language.code.equalsIgnoreCase(trimmed)) {
                        return language;
                    }
                }
            }
            return INDONESIAN;
        }
    }

    private static final String PREFERENCE_KEY = "language";
    private static final String BUNDLE_BASE = "com.example.obd2dashboard.i18n.messages";
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(Localization.class);
    private static final Map<Language, ResourceBundle> BUNDLES = new EnumMap<>(Language.class);

    private static volatile Language current = load();

    private Localization() {
    }

    public static Language language() {
        return current;
    }

    public static void setLanguage(Language language) {
        if (language == null) {
            throw new IllegalArgumentException("language must not be null");
        }
        current = language;
        PREFERENCES.put(PREFERENCE_KEY, language.code());
    }

    public static Locale locale() {
        return current.locale();
    }

    /**
     * Mengembalikan teks terterjemahkan untuk {@code key}. Argumen opsional memakai
     * placeholder {@code {0}}, {@code {1}}, ... seperti {@link MessageFormat}.
     */
    public static String text(String key, Object... args) {
        if (key == null) {
            return "";
        }
        Language language = current;
        String pattern = lookup(language, key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, language.locale()).format(args);
    }

    private static String lookup(Language language, String key) {
        ResourceBundle active = bundle(language);
        if (active.containsKey(key)) {
            return active.getString(key);
        }
        ResourceBundle fallback = bundle(Language.ENGLISH);
        return fallback.containsKey(key) ? fallback.getString(key) : key;
    }

    private static ResourceBundle bundle(Language language) {
        synchronized (BUNDLES) {
            return BUNDLES.computeIfAbsent(language,
                    l -> ResourceBundle.getBundle(BUNDLE_BASE, l.locale()));
        }
    }

    private static Language load() {
        return Language.fromCode(PREFERENCES.get(PREFERENCE_KEY, null));
    }

    /** Memuat ulang bahasa dari preferensi yang tersimpan. */
    static void reload() {
        current = load();
    }
}
