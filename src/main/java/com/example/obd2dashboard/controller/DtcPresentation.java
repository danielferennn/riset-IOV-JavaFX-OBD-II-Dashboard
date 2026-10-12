package com.example.obd2dashboard.controller;

import java.util.List;

/**
 * State tampilan daftar DTC. Logika murni tanpa JavaFX agar dapat diuji tanpa toolkit dan
 * menjamin kode lama tidak pernah muncul kembali saat pembacaan gagal atau kosong.
 */
final class DtcPresentation {
    enum State { NONE, LOADING, EMPTY, NO_CONNECTION, ERROR, RESULTS }

    /**
     * Hasil render untuk daftar DTC.
     *
     * @param placeholder {@code true} bila {@code message} harus ditampilkan sebagai placeholder
     *                    ListView (loading/empty/no-connection/error); {@code items} selalu kosong
     *                    dalam hal ini sehingga status tidak tampil sebagai baris yang dapat dipilih
     * @param items baris daftar; hanya berisi kode DTC asli pada state {@link State#RESULTS}
     * @param message teks status untuk placeholder, atau {@code null}
     */
    record Display(boolean placeholder, List<String> items, String message) {
    }

    private DtcPresentation() {
    }

    /**
     * Menghitung tampilan untuk {@code state}. Untuk state selain {@link State#RESULTS}, kode
     * {@code results} sengaja diabaikan sehingga hasil pembacaan lama tidak pernah tampil sebagai
     * kode saat ini setelah pembacaan gagal/kosong.
     */
    static Display display(State state, List<String> results, String message) {
        return switch (state) {
            case NONE -> new Display(false, List.of(), null);
            case LOADING, EMPTY, NO_CONNECTION, ERROR ->
                    new Display(true, List.of(), message == null ? "" : message);
            case RESULTS -> new Display(false, List.copyOf(results == null ? List.of() : results), null);
        };
    }
}
