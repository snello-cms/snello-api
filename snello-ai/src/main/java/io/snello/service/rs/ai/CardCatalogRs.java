package io.snello.service.rs.ai;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.management.SnelloPlane;
import io.snello.model.ai.AiUiCard;
import io.snello.service.ApiService;
import io.snello.service.ai.skills.ProjectAccess;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

import static io.snello.management.AppConstants.AI_CARD_CATALOG_PATH;
import static io.snello.management.AppConstants.AI_UI_CARD;

@Path(AI_CARD_CATALOG_PATH)
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class CardCatalogRs {

    @Inject
    ProjectAccess access;

    @Inject
    SecurityIdentity identity;

    @Inject
    ApiService apiService;

    @GET
    public Response enabled(@QueryParam("projectUuid") String projectUuid) throws Exception {
        if (projectUuid == null || projectUuid.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(List.of()).build();
        }
        var project = access.findByUuid(projectUuid.trim()).orElse(null);
        if (project == null || !access.canAccess(identity, project)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        MultivaluedHashMap<String, String> params = new MultivaluedHashMap<>();
        params.putSingle("project_uuid", project.uuid);
        params.putSingle("enabled", "true");
        List<Map<String, Object>> rows = apiService.list(AI_UI_CARD, params, "name asc", 200, 0);
        List<CardCatalogEntry> cards = rows == null ? List.of() : rows.stream()
                .map(AiUiCard::new)
                .map(CardCatalogEntry::from)
                .toList();
        return Response.ok(cards).build();
    }

    public record CardCatalogEntry(String resultType, String template, String name) {
        static CardCatalogEntry from(AiUiCard card) {
            return new CardCatalogEntry(card.result_type, card.template, card.name);
        }
    }
}
