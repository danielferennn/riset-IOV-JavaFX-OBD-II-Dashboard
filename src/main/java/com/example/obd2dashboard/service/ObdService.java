package com.example.obd2dashboard.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ObdService extends AutoCloseable {
    boolean isRunning();

    /**
     * Menghentikan producer telemetry. Future yang dikembalikan selesai secara sukses hanya
     * setelah producer benar-benar quiescent: tidak ada lagi callback telemetry/status yang
     * sedang berjalan maupun yang akan dipancarkan sesudah completion. Caller boleh memakai
     * completion future ini sebagai bukti bahwa tidak ada callback lama yang tertinggal.
     */
    CompletableFuture<Void> stop();

    CompletableFuture<List<String>> readDtc();

    @Override
    void close();
}
