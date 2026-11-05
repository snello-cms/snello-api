package io.snello.test.metadata;

import io.quarkus.test.junit.QuarkusTest;
import io.snello.model.Metadata;
import io.snello.service.ApiService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.snello.management.AppConstants.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class UserActionsMetadataTest {

    private static final String USER_ACTIONS = "user_actions";
    private static final String SEEDED_UUID = "0766dca0-7af0-48d3-a9b7-451eb3c50166";

    @Inject
    ApiService apiService;

    @Test
    void userActionsMetadataShouldBeOwnerProtected() throws Exception {
        Metadata metadata = apiService.metadata(USER_ACTIONS);

        assertNotNull(metadata);
        assertEquals(USER_ACTIONS, metadata.table_name);
        assertTrue(metadata.api_protected);
        assertEquals("action_username", metadata.username_field);
        assertEquals(UUID, metadata.table_key);
    }

    @Test
    void shouldLoadKnownSeededUserActionRow() throws Exception {
        Map<String, Object> row = apiService.fetch(null, USER_ACTIONS, SEEDED_UUID, UUID);

        assertNotNull(row);
        assertEquals(SEEDED_UUID, row.get("uuid"));
        assertEquals("snello@snello.io", row.get("action_username"));
        assertEquals("the-sandringham--st-kilda-run", row.get("data_table_key"));
        assertEquals("like", row.get("action_name"));
        assertEquals("places", row.get("data_table_name"));
    }
}
