package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.model.pojo.AuthUserRequest;
import io.snello.util.AuthUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
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

    private static final String GROUP_ADMIN = "Admin";
    private static final String GROUP_MANAGER = "Manager";
    private static final String GROUP_USER = "User";
    private static final String ROLE_ADMIN = "Admin";
    private static final String SNELLO_API_CLIENT_ID = "snello-api";
    private static final String GROUP_SUFFIX_EDIT = "_edit";
    private static final String GROUP_SUFFIX_VIEW = "_view";

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

    @ConfigProperty(name = "snello-admin.username")
    String bootstrapAdminUsername;

    @ConfigProperty(name = "snello-admin.password")
    String bootstrapAdminPassword;

    public void bootstrapBaseSecurity() {
        try (Keycloak keycloak = buildClient()) {
            RealmResource realm = keycloak.realm(targetRealm);

            Map<String, String> groupIdsByName = ensureBaseGroups(realm);
            ensureAtLeastOneAdminUser(realm, groupIdsByName);
        } catch (RuntimeException ex) {
            Log.error("Cannot bootstrap base Keycloak security", ex);
            throw new InternalServerErrorException("Cannot bootstrap Keycloak realm '" + targetRealm + "'");
        }
    }

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

    public Map<String, Object> createUser(AuthUserRequest request) {
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

                if (request.groupNames != null) {
                    syncUserGroupsByNames(realm, userId, request.groupNames);
                }
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
            Map<String, String> existingGroupIdsByName = loadAllGroupIdsByName(realm.groups().groups());
            Set<String> expectedMetadataGroups = expectedMetadataGroups(metadataNames);

            List<String> createdGroups = new ArrayList<>();
            List<String> existingGroups = new ArrayList<>();
            List<String> deletedGroups = new ArrayList<>();

            for (String metadataName : metadataNames) {
                String normalized = AuthUtils.trimToNull(metadataName);
                if (normalized == null) {
                    continue;
                }

                String editGroup = normalized + GROUP_SUFFIX_EDIT;
                String viewGroup = normalized + GROUP_SUFFIX_VIEW;

                ensureGroupExists(realm, existingGroupIdsByName.keySet(), editGroup, createdGroups, existingGroups);
                ensureGroupExists(realm, existingGroupIdsByName.keySet(), viewGroup, createdGroups, existingGroups);
            }

            int detachedMemberships = removeStaleMetadataGroups(realm, existingGroupIdsByName, expectedMetadataGroups, deletedGroups);

            Map<String, Object> result = new HashMap<>();
            result.put("metadatasCount", metadataNames.size());
            result.put("createdCount", createdGroups.size());
            result.put("existingCount", existingGroups.size());
            result.put("createdGroups", createdGroups);
            result.put("existingGroups", existingGroups);
            result.put("deletedCount", deletedGroups.size());
            result.put("deletedGroups", deletedGroups);
            result.put("detachedMemberships", detachedMemberships);
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

    private Set<String> expectedMetadataGroups(Set<String> metadataNames) {
        Set<String> expectedGroups = new HashSet<>();
        if (metadataNames == null || metadataNames.isEmpty()) {
            return expectedGroups;
        }

        for (String metadataName : metadataNames) {
            String normalized = AuthUtils.trimToNull(metadataName);
            if (normalized == null) {
                continue;
            }
            expectedGroups.add(normalized + GROUP_SUFFIX_EDIT);
            expectedGroups.add(normalized + GROUP_SUFFIX_VIEW);
        }
        return expectedGroups;
    }

    private int removeStaleMetadataGroups(RealmResource realm,
                                          Map<String, String> existingGroupIdsByName,
                                          Set<String> expectedMetadataGroups,
                                          List<String> deletedGroups) {
        int detachedMemberships = 0;

        for (Map.Entry<String, String> entry : existingGroupIdsByName.entrySet()) {
            String groupName = entry.getKey();
            String groupId = entry.getValue();

            if (!isManagedMetadataGroup(groupName) || expectedMetadataGroups.contains(groupName)) {
                continue;
            }

            var groupResource = realm.groups().group(groupId);
            List<UserRepresentation> members = groupResource.members();
            if (members != null) {
                for (UserRepresentation member : members) {
                    if (member.getId() == null || member.getId().isBlank()) {
                        continue;
                    }
                    realm.users().get(member.getId()).leaveGroup(groupId);
                    detachedMemberships++;
                }
            }

            groupResource.remove();
            deletedGroups.add(groupName);
            Log.info("Removed stale Keycloak metadata group: " + groupName);
        }

        return detachedMemberships;
    }

    private boolean isManagedMetadataGroup(String groupName) {
        String normalized = AuthUtils.trimToNull(groupName);
        if (normalized == null) {
            return false;
        }
        return normalized.endsWith(GROUP_SUFFIX_EDIT) || normalized.endsWith(GROUP_SUFFIX_VIEW);
    }

    public Map<String, Object> updateUser(String id, AuthUserRequest request) {
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

    private Set<String> resolveGroupIds(RealmResource realm,
                                        List<String> groupNames) {
        Set<String> resolved = new HashSet<>();

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
        Set<String> requestedGroupIds = resolveGroupIds(realm, groupNames);

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

    private void validateCreateRequest(AuthUserRequest request) {
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

    public Object listGroupUsers(String id) {
        if (id == null || id.isBlank()) {
            throw new BadRequestException("Group id is required");
        }

        try (Keycloak keycloak = buildClient()) {
            RealmResource realm = keycloak.realm(targetRealm);
            var groupResource = realm.groups().group(id);

            // Fail fast when group id is invalid.
            var groupRepresentation = groupResource.toRepresentation();
            if (groupRepresentation == null) {
                throw new NotFoundException("Group not found: " + id);
            }

            List<UserRepresentation> users = groupResource.members();
            List<Map<String, Object>> result = new ArrayList<>();
            for (UserRepresentation user : users) {
                result.add(AuthUtils.userToMap(user));
            }
            return result;
        } catch (BadRequestException | NotFoundException | InternalServerErrorException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            Log.error("Cannot list users for Keycloak group id=" + id, ex);
            throw new InternalServerErrorException(
                    "Cannot list users for group '" + id + "' in Keycloak realm '" + targetRealm + "'");
        }
    }

    public Object listUserGroups(String id) {
        if (id == null || id.isBlank()) {
            throw new BadRequestException("User id is required");
        }

        try (Keycloak keycloak = buildClient()) {
            RealmResource realm = keycloak.realm(targetRealm);
            UserResource userResource = realm.users().get(id);

            // Fail fast when user id is invalid.
            UserRepresentation userRepresentation = userResource.toRepresentation();
            if (userRepresentation == null) {
                throw new NotFoundException("User not found: " + id);
            }

            List<GroupRepresentation> groups = userResource.groups();
            List<Map<String, Object>> result = new ArrayList<>();
            for (GroupRepresentation group : groups) {
                result.add(AuthUtils.groupToMap(group));
            }
            return result;
        } catch (BadRequestException | NotFoundException | InternalServerErrorException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            Log.error("Cannot list groups for Keycloak user id=" + id, ex);
            throw new InternalServerErrorException(
                    "Cannot list groups for user '" + id + "' in Keycloak realm '" + targetRealm + "'");
        }
    }

    public Object getUser(String id) {
        if (id == null || id.isBlank()) {
            throw new BadRequestException("User id is required");
        }

        try (Keycloak keycloak = buildClient()) {
            RealmResource realm = keycloak.realm(targetRealm);
            UserResource userResource = realm.users().get(id);
            UserRepresentation userRepresentation = userResource.toRepresentation();

            if (userRepresentation == null) {
                throw new NotFoundException("User not found: " + id);
            }

            return AuthUtils.userToMap(userRepresentation);
        } catch (BadRequestException | NotFoundException | InternalServerErrorException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            Log.error("Cannot fetch Keycloak user id=" + id, ex);
            throw new InternalServerErrorException(
                    "Cannot fetch user '" + id + "' from Keycloak realm '" + targetRealm + "'");
        }
    }

    private Map<String, String> ensureBaseGroups(RealmResource realm) {
        Map<String, String> existingGroupIdsByName = loadAllGroupIdsByName(realm.groups().groups());
        ensureGroupExistsByName(realm, existingGroupIdsByName, GROUP_ADMIN);
        ensureGroupExistsByName(realm, existingGroupIdsByName, GROUP_MANAGER);
        ensureGroupExistsByName(realm, existingGroupIdsByName, GROUP_USER);
        return existingGroupIdsByName;
    }

    private void ensureGroupExistsByName(RealmResource realm,
                                         Map<String, String> groupIdsByName,
                                         String groupName) {
        if (groupIdsByName.containsKey(groupName)) {
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

            groupIdsByName.put(groupName, groupId);
            Log.info("Created missing Keycloak group: " + groupName);
        }
    }

    private void ensureAtLeastOneAdminUser(RealmResource realm,
                                           Map<String, String> groupIdsByName) {
        String clientInternalId = resolveClientInternalId(realm, SNELLO_API_CLIENT_ID);
        if (existsAdminUser(realm, clientInternalId)) {
            return;
        }

        String username = AuthUtils.trimToNull(bootstrapAdminUsername);
        String password = AuthUtils.trimToNull(bootstrapAdminPassword);

        if (username == null || password == null) {
            throw new InternalServerErrorException(
                    "Missing bootstrap admin credentials: snello-admin.username/snello-admin.password");
        }

        String userId = findUserIdByUsername(realm, username);
        if (userId == null) {
            userId = createBootstrapAdminUser(realm, username);
            Log.info("Created bootstrap Keycloak admin user: " + username);
        }

        UserResource userResource = realm.users().get(userId);
        setPassword(userResource, password);

        String adminGroupId = groupIdsByName.get(GROUP_ADMIN);
        if (adminGroupId != null && !isUserInGroup(userResource, GROUP_ADMIN)) {
            userResource.joinGroup(adminGroupId);
        }

        assignAdminClientRoleIfPossible(realm, userResource, clientInternalId);
    }

    private boolean existsAdminUser(RealmResource realm, String clientInternalId) {
        List<UserRepresentation> users = listAllUsers(realm);
        for (UserRepresentation user : users) {
            if (user.getId() == null || user.getId().isBlank()) {
                continue;
            }

            UserResource userResource = realm.users().get(user.getId());
            if (isUserInGroup(userResource, GROUP_ADMIN) || hasAdminClientRole(userResource, clientInternalId)) {
                return true;
            }
        }
        return false;
    }

    private String resolveClientInternalId(RealmResource realm, String clientId) {
        List<ClientRepresentation> clients = realm.clients().findByClientId(clientId);
        if (clients == null || clients.isEmpty()) {
            Log.warn("Keycloak client not found, skipping client-role assignment: " + clientId);
            return null;
        }
        return clients.getFirst().getId();
    }

    private String findUserIdByUsername(RealmResource realm, String username) {
        List<UserRepresentation> users = listAllUsers(realm);
        for (UserRepresentation user : users) {
            if (user.getUsername() != null && user.getUsername().equalsIgnoreCase(username)) {
                return user.getId();
            }
        }
        return null;
    }

    private String createBootstrapAdminUser(RealmResource realm, String username) {
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setEnabled(true);
        userRepresentation.setUsername(username);
        userRepresentation.setEmail(resolveBootstrapEmail(username));
        userRepresentation.setEmailVerified(true);
        userRepresentation.setFirstName("Bootstrap");
        userRepresentation.setLastName("Admin");

        try (Response response = realm.users().create(userRepresentation)) {
            int status = response.getStatus();
            if (status < 200 || status > 299) {
                String body = AuthUtils.safeEntity(response);
                throw new InternalServerErrorException(
                        "Keycloak bootstrap admin creation failed with status " + status + ": " + body);
            }

            String userId = CreatedResponseUtil.getCreatedId(response);
            if (userId == null || userId.isBlank()) {
                throw new InternalServerErrorException("Bootstrap admin created but id is missing");
            }
            return userId;
        }
    }

    private String resolveBootstrapEmail(String username) {
        if (username.contains("@")) {
            return username;
        }
        return username + "@local";
    }

    private void setPassword(UserResource userResource, String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        userResource.resetPassword(credential);
    }

    private boolean isUserInGroup(UserResource userResource, String groupName) {
        for (GroupRepresentation group : userResource.groups()) {
            String name = AuthUtils.trimToNull(group.getName());
            if (name != null && name.equalsIgnoreCase(groupName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAdminClientRole(UserResource userResource, String clientInternalId) {
        if (clientInternalId == null || clientInternalId.isBlank()) {
            return false;
        }

        List<RoleRepresentation> roles = userResource.roles().clientLevel(clientInternalId).listEffective();
        for (RoleRepresentation role : roles) {
            String roleName = AuthUtils.trimToNull(role.getName());
            if (roleName != null && roleName.equalsIgnoreCase(ROLE_ADMIN)) {
                return true;
            }
        }
        return false;
    }

    private void assignAdminClientRoleIfPossible(RealmResource realm,
                                                 UserResource userResource,
                                                 String clientInternalId) {
        if (clientInternalId == null || clientInternalId.isBlank()) {
            return;
        }
        if (hasAdminClientRole(userResource, clientInternalId)) {
            return;
        }

        RoleRepresentation adminRole;
        try {
            adminRole = realm.clients().get(clientInternalId).roles().get(ROLE_ADMIN).toRepresentation();
        } catch (RuntimeException ex) {
            Log.warn("Keycloak client role 'Admin' not found on client '" + SNELLO_API_CLIENT_ID + "'");
            return;
        }

        userResource.roles().clientLevel(clientInternalId).add(List.of(adminRole));
    }

    private List<UserRepresentation> listAllUsers(RealmResource realm) {
        List<UserRepresentation> all = new ArrayList<>();
        int first = 0;
        int size = 200;

        while (true) {
            List<UserRepresentation> page = realm.users().list(first, size);
            if (page == null || page.isEmpty()) {
                break;
            }
            all.addAll(page);
            if (page.size() < size) {
                break;
            }
            first += size;
        }

        return all;
    }
}
