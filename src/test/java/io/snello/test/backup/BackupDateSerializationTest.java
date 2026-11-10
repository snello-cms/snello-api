package io.snello.test.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.snello.service.BackupService;
import io.snello.util.SqlHelper;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backup ZIP must emit ISO date/time strings (REST contract), not Jackson epoch millis.
 */
class BackupDateSerializationTest {

    @Test
    void convertSqlValueFormatsSqlDateAsIsoLocalDate() throws Exception {
        Object converted = SqlHelper.convertSqlValue(Date.valueOf(LocalDate.of(2026, 10, 11)));
        assertEquals("2026-10-11", converted);
    }

    @Test
    void backupMapperDoesNotWriteDatesAsEpochMillis() throws Exception {
        ObjectMapper mapper = BackupService.backupObjectMapper();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uuid", "row-1");
        row.put("creation_date", Date.valueOf(LocalDate.of(2026, 10, 11)));
        row.put("updated_at", Timestamp.valueOf("2026-10-11 15:30:00"));

        String json = mapper.writeValueAsString(List.of(row));
        assertFalse(json.contains("1791669600000"), json);
        assertTrue(json.contains("2026-10-11"), json);
        assertFalse(json.matches("(?s).*\"creation_date\"\\s*:\\s*\\d+.*"), json);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parsed = mapper.readValue(json, List.class);
        assertInstanceOf(String.class, parsed.getFirst().get("creation_date"));
        assertEquals("2026-10-11", parsed.getFirst().get("creation_date"));
    }
}
