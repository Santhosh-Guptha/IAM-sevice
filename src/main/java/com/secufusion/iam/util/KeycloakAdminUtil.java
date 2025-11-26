package com.secufusion.iam.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.*;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.Response;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdminUtil {

    private final Keycloak keycloak;

    // ---------------------------
    // Create Realm
    // ---------------------------
    public void createRealm(String realmName) {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(realmName);
        realm.setEnabled(true);

        keycloak.realms().create(realm);
        log.info("Created realm '{}'", realmName);
    }

    // ---------------------------
    // Remove Realm
    // ---------------------------
    public void removeRealm(String realmName) {
        keycloak.realm(realmName).remove();
        log.info("Removed realm '{}'", realmName);
    }


    // ---------------------------
    // Create User
    // ---------------------------
    public String createUser(String realm, String username, String email, String fullName) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setEnabled(true);
        user.setEmailVerified(false);
        user.setFirstName(fullName);
        user.setCredentials(Collections.emptyList());

        Response response = keycloak.realm(realm).users().create(user);
        int status = response.getStatus();
        if (status != 201 && status != 409) {
            String body = response.readEntity(String.class);
            response.close();
            throw new RuntimeException("Failed to create user: status=" + status + " body=" + body);
        }
        String userId = org.keycloak.admin.client.CreatedResponseUtil.getCreatedId(response);
        response.close();
        log.info("Created Keycloak user '{}' (id={}) in realm '{}'", username, userId, realm);
        return userId;
    }

    // ---------------------------
    // Set Password
    // ---------------------------
    public void setPassword(String realm, String userId, String password, boolean temporary) {
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(temporary);
        keycloak.realm(realm).users().get(userId).resetPassword(cred);
        log.info("Set password for userId={} temporary={}", userId, temporary);
    }

    // ---------------------------
    // Send Required Action Emails
    // ---------------------------
    public void sendRequiredActionEmail(String realm, String userId, List<String> actions) {
        keycloak.realm(realm).users().get(userId).executeActionsEmail(actions);
        log.info("Sent required action email {} to userId={}", actions, userId);
    }

    public void assignAllRealmRoles(String realm, String userId) {

        RealmResource realmRes = keycloak.realm(realm);
        UsersResource usersRes = realmRes.users();

        List<RoleRepresentation> allRealmRoles = realmRes.roles()
                .list()
                .stream()
                .map(role -> realmRes.roles().get(role.getName()).toRepresentation())
                .toList();

        if (!allRealmRoles.isEmpty()) {
            usersRes.get(userId)
                    .roles()
                    .realmLevel()
                    .add(allRealmRoles);
        }

        log.info("Assigned ALL realm-level roles (count={}) to user {}",
                allRealmRoles.size(), userId);
    }

    public void assignAllClientRoles(String realm, String userId) {

        RealmResource realmRes = keycloak.realm(realm);
        UsersResource usersRes = realmRes.users();
        ClientsResource clientsRes = realmRes.clients();

        List<ClientRepresentation> clients = clientsRes.findAll();

        for (ClientRepresentation client : clients) {

            List<RoleRepresentation> clientRoles =
                    clientsRes.get(client.getId())
                            .roles()
                            .list()
                            .stream()
                            .map(r -> clientsRes
                                    .get(client.getId())
                                    .roles()
                                    .get(r.getName())
                                    .toRepresentation())
                            .toList();

            if (!clientRoles.isEmpty()) {

                usersRes.get(userId)
                        .roles()
                        .clientLevel(client.getId())
                        .add(clientRoles);

                log.info("Assigned {} roles from client '{}' to user {}",
                        clientRoles.size(), client.getClientId(), userId);
            }
        }

        log.info("Assigned ALL client-level roles for ALL clients to user {}", userId);
    }


    // ---------------------------
    // Get RealmRepresentation (optional)
    // ---------------------------
    public RealmRepresentation getRealm(String realmName) {
        return keycloak.realm(realmName).toRepresentation();
    }
}
