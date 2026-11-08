package io.snello.test.ai;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.service.ApiService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import tools.jackson.databind.json.JsonMapper;

import static io.restassured.RestAssured.given;
import static io.snello.management.AppConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Seeds the flower kayak.love AI catalog fixture and checks REST discovery endpoints.
 */
@QuarkusTest
class AiCatalogImportTest {

    @Inject
    ApiService apiService;

    @Inject
    JsonMapper mapper;

    @Test
    @TestSecurity(user = "catalog-tester", roles = {"View", "Admin"})
    void flowerFixtureSeedsAndIsVisibleOnCatalogApis() throws Exception {
        Map<String, Object> fixture;
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("fixtures/flower-ai-kayaklove.json")) {
            assertNotNull(in, "fixture missing");
            fixture = mapper.readValue(in, Map.class);
        }

        String projectUuid = upsertAll(fixture);

        List<Map<String, Object>> mine = given().accept(ContentType.JSON)
                .when().get("/api/ai/me/projects")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        assertTrue(mine.stream().anyMatch(p -> "kayak.love".equals(p.get("name"))), mine.toString());

        List<Map<String, Object>> cards = given().accept(ContentType.JSON)
                .when().get("/api/ai/card-catalog?projectUuid={uuid}", projectUuid)
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        Set<String> cardNames = cards.stream().map(c -> (String) c.get("name")).collect(Collectors.toSet());
        assertTrue(cardNames.contains("Kayak card"), cardNames.toString());

        List<Map<String, Object>> skills = given().accept(ContentType.JSON)
                .when().get("/api/ai/skills?_limit=200")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        Set<String> skillNames = skills.stream()
                .filter(s -> projectUuid.equals(s.get("project_uuid")))
                .map(s -> (String) s.get("name"))
                .collect(Collectors.toSet());
        assertTrue(skillNames.contains("ricerca-kayak"), skillNames.toString());
        assertEquals(7, skillNames.size(), skillNames.toString());

        List<Map<String, Object>> tools = given().accept(ContentType.JSON)
                .when().get("/api/ai/mcp-tools?_limit=200")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        Set<String> toolNames = tools.stream()
                .filter(t -> projectUuid.equals(t.get("project_uuid")))
                .map(t -> (String) t.get("name"))
                .collect(Collectors.toSet());
        assertTrue(toolNames.contains("searchKayaks"), toolNames.toString());
        assertEquals(11, toolNames.size(), toolNames.toString());
    }

    @SuppressWarnings("unchecked")
    private String upsertAll(Map<String, Object> fixture) throws Exception {
        List<Map<String, Object>> projects = (List<Map<String, Object>>) fixture.get("projects");
        Map<String, Object> project = projects.getFirst();
        String projectUuid = (String) project.get("uuid");
        upsert(AI_PROJECT, project);

        for (Map<String, Object> row : (List<Map<String, Object>>) fixture.get("skills")) {
            upsert(AI_SKILL, row);
        }
        for (Map<String, Object> row : (List<Map<String, Object>>) fixture.get("mcp_tools")) {
            Map<String, Object> clean = row.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, java.util.LinkedHashMap::new));
            upsert(AI_MCP_TOOL, clean);
        }
        for (Map<String, Object> row : (List<Map<String, Object>>) fixture.get("cards")) {
            upsert(AI_UI_CARD, row);
        }
        return projectUuid;
    }

    private void upsert(String table, Map<String, Object> row) throws Exception {
        String uuid = (String) row.get(UUID);
        Map<String, Object> existing = apiService.fetch(null, table, uuid, UUID);
        if (existing == null) {
            apiService.createIfNotExists(table, row, UUID);
        } else {
            apiService.mergeIfNotExists(table, row, uuid, UUID);
        }
    }
}
