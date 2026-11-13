package io.snello.service;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Plain unit test (no Quarkus): zone of a print template's {@code now} ({@code snello.print.timezone}). */
class PrintTemplateZoneTest {

    private static PrintTemplateService service(Optional<String> tz) {
        PrintTemplateService s = new PrintTemplateService();
        s.timezone = tz;
        return s;
    }

    @Test
    void configuredZoneIsUsed() {
        assertEquals(ZoneId.of("Europe/Rome"), service(Optional.of("Europe/Rome")).zone());
    }

    @Test
    void missingOrBlankFallsBackToServerZone() {
        assertEquals(ZoneId.systemDefault(), service(Optional.empty()).zone());
        assertEquals(ZoneId.systemDefault(), service(Optional.of("  ")).zone());
    }
}
