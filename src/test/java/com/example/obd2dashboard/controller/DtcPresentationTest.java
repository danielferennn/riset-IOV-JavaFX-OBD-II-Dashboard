package com.example.obd2dashboard.controller;

import com.example.obd2dashboard.controller.DtcPresentation.Display;
import com.example.obd2dashboard.controller.DtcPresentation.State;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtcPresentationTest {
    private static final List<String> STALE = List.of("P0300", "P0171");

    @Test
    void resultsPutCodesInItemsAndNoPlaceholder() {
        Display display = DtcPresentation.display(State.RESULTS, STALE, null);
        assertFalse(display.placeholder());
        assertEquals(STALE, display.items());
        assertNull(display.message());
    }

    @Test
    void failedReadPutsStatusInPlaceholderAndNoCodesInItems() {
        Display display = DtcPresentation.display(State.ERROR, STALE, "Failed to read DTC");
        assertTrue(display.placeholder());
        assertEquals(List.of(), display.items(), "status must not be an item; stale codes must be gone");
        assertEquals("Failed to read DTC", display.message());
    }

    @Test
    void emptyResultUsesPlaceholderNotItems() {
        Display display = DtcPresentation.display(State.EMPTY, STALE, "No DTCs");
        assertTrue(display.placeholder());
        assertEquals(List.of(), display.items());
        assertEquals("No DTCs", display.message());
    }

    @Test
    void loadingStateUsesPlaceholderNotItems() {
        Display display = DtcPresentation.display(State.LOADING, STALE, "Reading DTCs...");
        assertTrue(display.placeholder());
        assertEquals(List.of(), display.items());
        assertEquals("Reading DTCs...", display.message());
    }

    @Test
    void noConnectionStateUsesPlaceholderNotItems() {
        Display display = DtcPresentation.display(State.NO_CONNECTION, STALE, "No active connection");
        assertTrue(display.placeholder());
        assertEquals(List.of(), display.items());
        assertEquals("No active connection", display.message());
    }

    @Test
    void noneStateHasNoPlaceholderAndNoItems() {
        Display display = DtcPresentation.display(State.NONE, STALE, null);
        assertFalse(display.placeholder());
        assertTrue(display.items().isEmpty());
        assertNull(display.message());
    }

    @Test
    void resultsStateWithNoCodesHasNoPlaceholder() {
        Display display = DtcPresentation.display(State.RESULTS, List.of(), null);
        assertFalse(display.placeholder());
        assertTrue(display.items().isEmpty());
        assertNull(display.message());
    }

    @Test
    void nullMessageInPlaceholderStateRendersEmptyTextNotNull() {
        Display display = DtcPresentation.display(State.LOADING, STALE, null);
        assertTrue(display.placeholder());
        assertEquals("", display.message());
    }
}
