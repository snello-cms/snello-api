package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.model.pojo.AuthCreateUserRequest;
import io.snello.model.pojo.AuthUpdateUserRequest;
import io.snello.util.AuthUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class AuthService {

    @Inject
    MetadataService metadataService;

    @ConfigProperty(name = "snello.keycloak.admin.server-url")
    String serverUrl;

    @ConfigProperty(name = "snello.keycloak.admin.auth-realm")
    String authRealm;

    @ConfigProperty(name = "snello.keycloak.admin.target-realm")
    String targetRealm;

    @ConfigProperty(name = "snello.keycloak.admin.client-id")
    String clientId;

    @ConfigProperty(name = "snello.keycloak.admin.grant-type", defaultValue = "password")
    String grantType;

    @ConfigProperty(name = "snello.keycloak.admin.username")
    String adminUsername;

    @ConfigProperty(name = "snello.keycloak.admin.password")
    String adminPassword;

    public List<Map<String, Object>> listUsers() {
        try (Keycloak keycloak = buildClient()) {
            RealmResource realm = keycloak.realm(targetRealm);
            List<UserRepresentation> users = realm.users().list();
            List<Map<String, Object>> result = new ArrayList<>();
            for (UserRepresentation user : users) {
                result.add(AuthUtils.userToMap(user));
            }
            return result;
        } catch (RuntimeException ex) {
            Log.error("Cannot list Keycloak users", ex);
            throw new InternalServerErrorException("Cannot list users from Keycloak realm '" + targetRealm + "'");
        }
    }

    public List<Map<String, Object>> listGroups() {
        try (Keycloak keycloak = buildClient()) {
            RealmResource realm = keycloak.realm(targetRealm);
            List<GroupRepresentation> groups = realm.groups().groups();
            List<Map<String, Object>> result = new ArrayList<>();
            for (GroupRepresentation group : groups) {
                result.add(AuthUtils.groupToMap(group));
            }
            return result;
        } catch (RuntimeException ex) {
            Log.error("Cannot list Keycloak groups", ex);
            throw new InternalServerErrorException("Cannot list groups from Keycloak realm '" + targetRealm + "'");
        }
    }

    public Map<String, Object> createUser(AuthCreateUserRequest request) {
        validateCreateRequest(request);

        try (Keycloak keycloak = buildClient()) {
            RealmResource realm = keycloak.realm(targetRealm);
            UserRepresentation userRepresentation = new UserRepresentation();
            userRepresentation.setEnabled(true);
            userRepresentation.setUsername(request.username.trim());
            userRepresentation.setEmail(request.email.trim());
            userRepresentation.setFirstName(AuthUtils.trimToNull(request.name));
            userRepresentation.setLastName(AuthUtils.trimToNull(request.surname));

            try (var response = realm.users().create(userRepresentation)) {
                int status = response.getStatus();
                if (status < 200 || status > 299) {
                    String body = AuthUtils.safeEntity(response);
                    throw new InternalServerErrorException(
                            "Keycloak user creation failed with status " + status + ": " + body);
                }

                String userId = CreatedResponseUtil.getCreatedId(response);
                if (userId == null || userId.isBlank()) {
                    throw new InternalServerErrorException("User created but id is missing in Keycloak response");
                }

                updateUserGroups(realm, userId, request.groupIds, request.groupNames);
                UserRepresentation created = realm.users().get(userId).toRepresentation();
                return AuthUtils.userToMap(created);
            }
        } catch (BadRequestException | InternalServerErrorException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            Log.error("Cannot create Keycloak user", ex);
            throw new InternalServerErrorException("Cannot create user in Keycloak realm '" + targetRealm + "'");
        }
    }

    public Map<String, Object> createGroups() {
        try (Keycloak keycloak = buildClient()) {
            RealmResource realm = keycloak.realm(targetRealm);

            Set<String> metadataNames = metadataService.names();
            Set<String> existingGroupNames = loadAllGroupNames(realm.groups().groups());

            List<String> createdGroups = new ArrayList<>();
            List<String> existingGroups = new ArrayList<>();

            for (String metadataName : metadataNames) {
                String normalized = AuthUtils.trimToNull(metadataName);
                if (normalized == null) {
                    continue;
                }

                String editGroup = normalized + "_edit";
                String viewGroup = normalized + "_view";

                ensureGroupExists(realm, existingGroupNames, editGroup, createdGroups, existingGroups);
                ensureGroupExists(realm, existingGroupNames, viewGroup, createdGroups, existingGroups);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("metadatasCount", metadataNames.size());
            result.put("createdCount", createdGroups.size());
            result.put("existingCount", existingGroups.size());
            result.put("createdGroups", createdGroups);
            result.put("existingGroups", existingGroups);
            return result;
        } catch (BadRequestException | InternalServerErrorException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            Log.error("Cannot ensure Keycloak groups for metadata", ex);
            throw new InternalServerErrorException("Cannot ensure metadata groups in Keycloak realm '" + targetRealm + "'");
        } catch (Exception ex) {
            Log.error("Cannot read metadata names", ex);
            throw new InternalServerErrorException("Cannot read metadata names to create groups");
        }
    }

    private Set<String> loadAllGroupNames(List<GroupRepresentation> groups) {
        Set<String> names = new HashSet<>();
        if (groups == null || groups.isEmpty()) {
            return names;
        }
        for (GroupRepresentation group : groups) {
            String groupName = AuthUtils.trimToNull(group.getName());
            if (groupName != null) {
                names.add(groupName);
            }
            if (group.getSubGroups() != null && !group.getSubGroups().isEmpty()) {
                names.addAll(loadAllGroupNames(group.getSubGroups()));
            }
        }
        return names;
    }

    private void ensureGroupExists(RealmResource realm,
                                   Set<String> existingGroupNames,
                                   String groupName,
                                   List<String> createdGroups,
                                   List<String> existingGroups) {
        if (existingGroupNames.contains(groupName)) {
            existingGroups.add(groupName);
            return;
        }

        GroupRepresentation groupRepresentation = new GroupRepresentation();
        groupRepresentation.setName(groupName);
        try (Response response = realm.groups().add(groupRepresentation)) {
            int status = response.getStatus();
            if (status < 200 || status > 299) {
                String body = AuthUtils.safeEntity(response);
                throw new InternalServerErrorException(
                        "Keycloak group creation failed for '" + groupName + "' with status " + status + ": " + body);
            }
            String groupId = CreatedResponseUtil.getCreatedId(response);
            if (groupId == null || groupId.isBlank()) {
                throw new InternalServerErrorException("Group created but id is missing for '" + groupName + "'");
            }
            existingGroupNames.add(groupName);
            createdGroups.add(groupName);
        }
    }

    public Map<String, Object> updateUser(String id, AuthUpdateUserRequest request) {
        if (id == null || id.isBlank()) {
            throw new BadRequestException("User id is required");
        }

        try (Keycloak keycloak = buildClient()) {
            RealmResource realm = keycloak.realm(targetRealm);
            UserResource userResource = realm.users().get(id);
            UserRepresentation existing = userResource.toRepresentation();
            if (existing == null) {
                throw new NotFoundException("User not found: " + id);
            }

            if (request != null) {
                if (request.username != null) {
                    existing.setUsername(AuthUtils.trimToNull(request.username));
                }
                if (request.email != null) {
                    existing.setEmail(AuthUtils.trimToNull(request.email));
                }
                if (request.name != null) {
                    existing.setFirstName(AuthUtils.trimToNull(request.name));
                }
                if (request.surname != null) {
                    existing.setLastName(AuthUtils.trimToNull(request.surname));
                }
            }

            userResource.update(existing);

            // On update the provided groupNames are treated as desired final state.
            // Missing groups are added and stale groups are removed.
            if (request != null && request.groupNames != null) {
                syncUserGroupsByNames(realm, id, request.groupNames);
            }

            UserRepresentation updated = userResource.toRepresentation();
            return AuthUtils.userToMap(updated);
        } catch (BadRequestException | NotFoundException | InternalServerErrorException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            Log.error("Cannot update Keycloak user", ex);
            throw new InternalServerErrorException("Cannot update user in Keycloak realm '" + targetRealm + "'");
        }
    }

    private Keycloak buildClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(authRealm)
                .grantType(grantType)
                .clientId(clientId)
                .username(adminUsername)
                .password(adminPassword)
                .build();
    }

    private void updateUserGroups(RealmResource realm,
                                  String userId,
                                  List<String> groupIds,
                                  List<String> groupNames) {
        Set<String> requestedGroupIds = resolveGroupIds(realm, groupIds, groupNames);
        if (requestedGroupIds.isEmpty()) {
            return;
        }

        UserResource userResource = realm.users().get(userId);
        Set<String> currentGroupIds = new HashSet<>();
        for (GroupRepresentation group : userResource.groups()) {
            currentGroupIds.add(group.getId());
        }

        for (String currentGroupId : currentGroupIds) {
            if (!requestedGroupIds.contains(currentGroupId)) {
                userResource.leaveGroup(currentGroupId);
            }
        }

        for (String requestedGroupId : requestedGroupIds) {
            if (!currentGroupIds.contains(requestedGroupId)) {
                userResource.joinGroup(requestedGroupId);
            }
        }
    }

    private Set<String> resolveGroupIds(RealmResource realm,
                                        List<String> groupIds,
                                        List<String> groupNames) {
        Set<String> resolved = new HashSet<>();
        if (groupIds != null) {
            for (String groupId : groupIds) {
                String value = AuthUtils.trimToNull(groupId);
                if (value != null) {
                    resolved.add(value);
                }
            }
        }

        if (groupNames != null && !groupNames.isEmpty()) {
            Map<String, String> idByName = loadAllGroupIdsByName(realm.groups().groups());

            for (String groupName : groupNames) {
                String normalized = AuthUtils.trimToNull(groupName);
                if (normalized == null) {
                    continue;
                }
                String id = idByName.get(normalized);
                if (id == null) {
                    throw new BadRequestException("Group not found: " + normalized);
                }
                resolved.add(id);
            }
        }

        return resolved;
    }

    private void syncUserGroupsByNames(RealmResource realm,
                                       String userId,
                                       List<String> groupNames) {
        Set<String> requestedGroupIds = resolveGroupIds(realm, null, groupNames);

        UserResource userResource = realm.users().get(userId);
        Set<String> currentGroupIds = new HashSet<>();
        for (GroupRepresentation group : userResource.groups()) {
            currentGroupIds.add(group.getId());
        }

        for (String currentGroupId : currentGroupIds) {
            if (!requestedGroupIds.contains(currentGroupId)) {
                userResource.leaveGroup(currentGroupId);
            }
        }

        for (String requestedGroupId : requestedGroupIds) {
            if (!currentGroupIds.contains(requestedGroupId)) {
                userResource.joinGroup(requestedGroupId);
            }
        }
    }

    private Map<String, String> loadAllGroupIdsByName(List<GroupRepresentation> groups) {
        Map<String, String> idByName = new HashMap<>();
        if (groups == null || groups.isEmpty()) {
            return idByName;
        }

        for (GroupRepresentation group : groups) {
            if (group.getName() != null && group.getId() != null) {
                idByName.put(group.getName(), group.getId());
            }
            if (group.getSubGroups() != null && !group.getSubGroups().isEmpty()) {
                idByName.putAll(loadAllGroupIdsByName(group.getSubGroups()));
            }
        }
        return idByName;
    }

    private void validateCreateRequest(AuthCreateUserRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        if (AuthUtils.trimToNull(request.username) == null) {
            throw new BadRequestException("Field 'username' is required");
        }
        if (AuthUtils.trimToNull(request.email) == null) {
            throw new BadRequestException("Field 'email' is required");
        }
    }
}
