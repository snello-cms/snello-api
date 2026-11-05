package io.snello.test.lovers;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;

@QuarkusTest
class LoversFetchNotFoundTest {

    @Test
    @TestSecurity(user = "ghost-user", roles = {"User"})
    void shouldReturnNotFoundWhenLoverUuidDoesNotExistWithValidAuthenticatedUser() {
        String missingUuid = UUID.randomUUID().toString();

        given()
                .accept(ContentType.JSON)
                .header("Authorization", "Bearer valid-test-token")
                .when()
                .get("/api/lovers/{uuid}", missingUuid)
                .then()
                .statusCode(404);
    }
}
