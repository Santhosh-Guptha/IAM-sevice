package com.secufusion.iam.util;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdminUtil {

    private final Keycloak keycloak;

    // ---------------- SMTP properties (Option A: @Value inside util) ----------------
    @Value("${mail.smtp.host}")
    private String smtpHost;

    @Value("${mail.smtp.port}")
    private String smtpPort;

    @Value("${mail.smtp.auth}")
    private String smtpAuth;

    @Value("${mail.smtp.starttls}")
    private String smtpStarttls;

    @Value("${mail.smtp.username}")
    private String smtpUsername;

    @Value("${mail.smtp.password}")
    private String smtpPassword;

    @Value("${mail.smtp.mail}")
    private String smtpMail;

    // ============================================================
    // REALM OPERATIONS
    // ============================================================
    public boolean realmExists(String realm) {
        try {
            boolean exists = keycloak.realms().findAll().stream()
                    .anyMatch(r -> r.getRealm().equalsIgnoreCase(realm));
            log.debug("Realm exists check: realm={}, exists={}", realm, exists);
            return exists;
        } catch (Exception e) {
            log.error("Error checking realm existence. realm={}, error={}", realm, e.getMessage(), e);
            return false;
        }
    }

    public void createRealm(RealmRepresentation realmRepresentation) {
        String realmName = realmRepresentation.getRealm();
        log.info("Creating realm '{}'", realmName);
        try {
            keycloak.realms().create(realmRepresentation);
            log.info("Realm created: {}", realmName);
        } catch (Exception e) {
            log.error("Failed to create realm {}: {}", realmName, e.getMessage(), e);
            throw e;
        }
    }

    public void deleteRealm(String realmName) {
        log.info("Deleting realm: {}", realmName);
        try {
            keycloak.realm(realmName).remove();
            log.info("Realm deleted: {}", realmName);
        } catch (Exception e) {
            log.error("Failed to delete realm {}: {}", realmName, e.getMessage(), e);
            throw e;
        }
    }

    // ============================================================
    // CLIENT OPERATIONS
    // ============================================================
    public boolean clientExists(String realm, String clientId) {
        try {
            boolean exists = keycloak.realm(realm).clients().findAll()
                    .stream()
                    .anyMatch(c -> c.getClientId().equalsIgnoreCase(clientId));
            log.debug("Client exists check: realm={}, clientId={}, exists={}", realm, clientId, exists);
            return exists;
        } catch (Exception e) {
            log.error("Error checking client existence. realm={}, client={}, error={}", realm, clientId, e.getMessage(), e);
            return false;
        }
    }

    public void createClient(String realm, ClientRepresentation clientRep) {
        log.info("Creating Keycloak client: realm={}, clientId={}", realm, clientRep.getClientId());
        try {
            Response resp = keycloak.realm(realm).clients().create(clientRep);
            int status = resp.getStatus();
            log.debug("Client creation response status={}", status);
            if (status != 201 && status != 409) {
                String body = resp.readEntity(String.class);
                resp.close();
                log.error("Failed client creation. realm={}, clientId={}, response={}", realm, clientRep.getClientId(), body);
                throw new RuntimeException("Client creation failed: " + body);
            }
            resp.close();
            log.info("Client created in realm={} clientId={}", realm, clientRep.getClientId());
        } catch (Exception e) {
            log.error("Exception while creating client in realm {}: {}", realm, e.getMessage(), e);
            throw e;
        }
    }

    // ============================================================
    // USER OPERATIONS
    // ============================================================
    public String createUser(String realm, String username, String email, String firstName, String lastName) {
        log.info("Creating Keycloak user in realm='{}' username='{}'", realm, username);

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(false);

        try {
            Response response = keycloak.realm(realm).users().create(user);
            int status = response.getStatus();

            if (status == 409) {
                log.warn("User already exists in realm={} username={}", realm, username);
                response.close();
                return null;
            }

            if (status != 201) {
                String body = response.readEntity(String.class);
                response.close();
                log.error("Keycloak user creation failed: {}", body);
                throw new RuntimeException("Failed to create user: " + body);
            }

            String userId = CreatedResponseUtil.getCreatedId(response);
            response.close();
            log.info("Created Keycloak user {} in realm {} with id {}", username, realm, userId);
            return userId;
        } catch (Exception e) {
            log.error("Error creating user in Keycloak realm='{}' username='{}' : {}", realm, username, e.getMessage(), e);
            throw e;
        }
    }

    public void updateUser(String realm, String userId, String username, String email, String firstName, String lastName) {
        log.info("Updating KC user '{}' in realm '{}'", userId, realm);
        try {
            UsersResource users = keycloak.realm(realm).users();
            UserRepresentation rep = users.get(userId).toRepresentation();
            rep.setUsername(username);
            rep.setEmail(email);
            rep.setFirstName(firstName);
            rep.setLastName(lastName);
            users.get(userId).update(rep);
            log.info("Updated KC user {} in realm {}", userId, realm);
        } catch (Exception e) {
            log.error("Failed to update KC user {} in realm {}: {}", userId, realm, e.getMessage(), e);
            throw e;
        }
    }

    public void removeUser(String realm, String userId) {
        log.info("Removing KC user '{}' from realm '{}'", userId, realm);
        try {
            keycloak.realm(realm).users().get(userId).remove();
            log.info("Removed KC user {}", userId);
        } catch (Exception e) {
            log.error("Failed to remove KC user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    public void setPassword(String realm, String userId, String password, boolean temporary) {
        log.info("Setting password for KC user '{}' in realm '{}'", userId, realm);
        try {
            CredentialRepresentation cred = new CredentialRepresentation();
            cred.setType(CredentialRepresentation.PASSWORD);
            cred.setValue(password);
            cred.setTemporary(temporary);
            keycloak.realm(realm).users().get(userId).resetPassword(cred);
            log.debug("Password set for user {}", userId);
        } catch (Exception e) {
            log.error("Failed to set password for KC user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    public void sendRequiredActionEmail(String realm, String userId, List<String> actions) {
        log.info("Sending required-action email to KC user '{}' in realm {}", userId, realm);
        try {
            keycloak.realm(realm).users().get(userId).executeActionsEmail(actions);
            log.debug("Required-action email triggered for user {}", userId);
        } catch (Exception e) {
            log.error("Failed to send required-action email to KC user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    // Assign all client roles for the user (useful when you want to mirror roles across clients)
    public void assignAllClientRoles(String realm, String userId) {
        log.info("Assigning all client roles to user {} in realm {}", userId, realm);
        try {
            RealmResource rr = keycloak.realm(realm);
            List<ClientRepresentation> clients = rr.clients().findAll();
            for (ClientRepresentation client : clients) {
                List<RoleRepresentation> roles = rr.clients().get(client.getId()).roles().list();
                if (!roles.isEmpty()) {
                    rr.users().get(userId).roles().clientLevel(client.getId()).add(roles);
                    log.debug("Assigned {} roles from client {} to user {}", roles.size(), client.getClientId(), userId);
                }
            }
            log.info("Assigned client roles to user {}", userId);
        } catch (Exception e) {
            log.error("Failed assigning all client roles to KC user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    public void assignRealmAdminRole(String realm, String userId) {
        log.info("Assigning realm-admin role to user {} in realm {}", userId, realm);
        try {
            RealmResource rr = keycloak.realm(realm);
            ClientRepresentation realmMgmt = rr.clients().findByClientId("realm-management").get(0);
            RoleRepresentation role = rr.clients().get(realmMgmt.getId()).roles().get("realm-admin").toRepresentation();
            rr.users().get(userId).roles().clientLevel(realmMgmt.getId()).add(List.of(role));
            log.info("Assigned realm-admin to user {}", userId);
        } catch (Exception e) {
            log.error("Failed to assign realm-admin role to user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    // ============================================================
    // FIND / SEARCH HELPERS
    // ============================================================
    public List<UserRepresentation> findUserByUsername(String realm, String username) {
        try {
            return keycloak.realm(realm).users().search(username, true);
        } catch (Exception e) {
            log.error("Failed searching user in KC realm={} username={} : {}", realm, username, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public List<UserRepresentation> findUsersByUsernameOrEmail(String realm, String username, String email) {
        UsersResource users = keycloak.realm(realm).users();
        List<UserRepresentation> list = new ArrayList<>();
        try {
            if (username != null && !username.isBlank()) list.addAll(users.search(username, true));
            if (email != null && !email.isBlank()) list.addAll(users.search(email, true));
        } catch (Exception e) {
            log.error("Error searching KC for username/email realm={}, username={}, email={} : {}", realm, username, email, e.getMessage(), e);
            // return what we have (could be empty)
        }
        // dedupe by id
        return list.stream().collect(Collectors.toMap(UserRepresentation::getId, u -> u, (a,b) -> a)).values().stream().toList();
    }

    // Compares a DB user id to a KC id (in many flows DB userID != KC ID, but we provide utility)
    public boolean isSameKeycloakUser(String realm, String dbUserId, String kcUserId) {
        // If you use same IDs across DB and KC then compare; otherwise you may want to resolve mapping.
        boolean same = kcUserId != null && dbUserId != null && kcUserId.equals(dbUserId);
        log.debug("isSameKeycloakUser realm={}, dbUserId={}, kcUserId={}, same={}", realm, dbUserId, kcUserId, same);
        return same;
    }

    // ============================================================
    // EMAIL (moved here from TenantService)
    // ============================================================
    public void sendWelcomeEmail(String to, String loginUrl, String username) throws MessagingException {
        log.info("Sending welcome email to {} with loginUrl={}", to, loginUrl);

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", smtpAuth);
        props.put("mail.smtp.starttls.enable", smtpStarttls);

        Session session = Session.getInstance(
                props,
                new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(smtpUsername, smtpPassword);
                    }
                });

        Message msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(smtpMail, false));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        msg.setSubject("Welcome to Secufusion");
        msg.setContent(
                "<h3>Welcome to Secufusion!</h3>" +
                        "<p>Your admin account is ready.</p>" +
                        "<p><b>Login:</b> <a href='" + loginUrl + "'>" + loginUrl + "</a></p>" +
                        "<p><b>Username:</b> " + username + "</p><hr/>",
                "text/html"
        );

        Transport.send(msg);
        log.info("Welcome email sent to {}", to);
    }
}
