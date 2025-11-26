package com.secufusion.iam.service;

import com.secufusion.iam.dto.CreateTenantRequest;
import com.secufusion.iam.dto.TenantResponse;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.TenantType;
import com.secufusion.iam.entity.User;
import com.secufusion.iam.exception.KeycloakOperationException;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.TenantTypeRepository;
import com.secufusion.iam.repository.UserRepository;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class TenantService {

    private final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final Keycloak keycloak;

    @Value("${keycloak.admin.server-url}")
    private String baseUrl;

    @Autowired
    private TenantTypeRepository tenantTypeRepository;

    public TenantService(TenantRepository tenantRepository,
                         UserRepository userRepository,
                         Keycloak keycloak) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.keycloak = keycloak;
    }

    @Value("${realm.url}")
    private String realmUrl;

    @Value("${redirect.url}")
    private String redirectUrl;

    // =============================================================================================
    // CREATE TENANT
    // =============================================================================================

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest req) {

        log.info("Starting createTenant for realm='{}'", req.getTenantName());

        // ------------------------------ CREATE TENANT RECORD ------------------------------
        Tenant tenant = new Tenant();
        tenant.setTenantName(req.getTenantName());
        tenant.setRealmName(req.getTenantName());
        tenant.setDomain(req.getDomain());
        tenant.setRegion(req.getRegion());
        tenant.setPhoneNo(req.getPhoneNo());
        tenant.setTenantType(req.getTenantType());
        tenant.setIndustry(req.getIndustry());
        tenant.setTemporaryAddress(req.getTemporaryAddress());
        tenant.setPermanentAddress(req.getPermanentAddress());
        tenant.setBillingAddress(req.getBillingAddress());
        tenant.setStatus("CREATING");
        tenant.setBillingCycleType(req.getBillingCycleType());

        Tenant savedTenant = tenantRepository.save(tenant);
        log.info("Saved tenant in DB id={}", savedTenant.getTenantID());

        // ------------------------------ CREATE ADMIN USER (LOCAL DB) ------------------------------
        User admin = new User();
        admin.setFirstName(req.getAdminFirstName());
        admin.setLastName(req.getAdminLastName());
        admin.setUserName(req.getAdminUserName());
        admin.setEmail(req.getAdminEmail());
        admin.setPhoneNo(req.getPhoneNo());
        admin.setStatus("CREATING");
        admin.setTenant(savedTenant);
        admin.setDefaultUser(true);

        User savedUser = userRepository.save(admin);

        savedTenant.setUsers(List.of(savedUser));
        tenantRepository.save(savedTenant);

        // ------------------------------ SMTP CONFIG ------------------------------
        Map<String, String> smtpConfig = new HashMap<>();
        smtpConfig.put("host", "smtp.hostinger.com");
        smtpConfig.put("port", "587");
        smtpConfig.put("from", "no-reply@motivitylabs.net");
        smtpConfig.put("user", "no-reply@motivitylabs.net");
        smtpConfig.put("password", "Motivity@net@123");
        smtpConfig.put("auth", "true");
        smtpConfig.put("starttls", "true");
        smtpConfig.put("ssl", "false");

        // ------------------------------ CREATE REALM ------------------------------
        log.info("Creating Keycloak realm '{}'", req.getTenantName());

        RealmRepresentation realmRep = new RealmRepresentation();
        realmRep.setRealm(req.getTenantName());
        realmRep.setEnabled(true);
        realmRep.setSmtpServer(smtpConfig);

        try {
            keycloak.realms().create(realmRep);
            log.info("Realm '{}' created", req.getTenantName());
        } catch (WebApplicationException ex) {
            int status = ex.getResponse().getStatus();
            if (status == 409) {
                throw new KeycloakOperationException("REALM_ALREADY_EXISTS", 1005,
                        "A tenant environment with this name already exists.");
            }
            throw new KeycloakOperationException("REALM_CREATION_FAILED", 1006,
                    "Unable to create tenant environment.");
        } catch (Exception ex) {
            throw new KeycloakOperationException("INTERNAL_ERROR", 1013,
                    "Unexpected error occurred, please try again later.");
        }

        // ------------------------------ CREATE CLIENT ------------------------------
        log.info("Creating client '{}' in realm '{}'", req.getTenantName(), req.getTenantName());

        ClientRepresentation clientRep = new ClientRepresentation();
        clientRep.setClientId(req.getTenantName());
        clientRep.setName(req.getTenantName());
        clientRep.setProtocol("openid-connect");
        clientRep.setPublicClient(true);
        clientRep.setRedirectUris(Collections.singletonList(req.getDomain()));
        clientRep.setWebOrigins(Collections.singletonList("*"));
        clientRep.setStandardFlowEnabled(true);
        clientRep.setEnabled(true);

        Response clientResp = keycloak.realm(req.getTenantName()).clients().create(clientRep);
        int clientStatus = clientResp.getStatus();
        if (clientStatus != 201 && clientStatus != 409) {
            throw new KeycloakOperationException("CLIENT_CREATION_FAILED", 1008,
                    "Unable to configure login access.");
        }
        clientResp.close();

        String loginUrl = String.format(
                "%s/realms/%s/protocol/openid-connect/auth?client_id=%s&redirect_uri=%s&response_type=code",
                baseUrl,
                req.getTenantName(),
                req.getTenantName(),
                URLEncoder.encode(req.getDomain(), StandardCharsets.UTF_8)
        );

        savedTenant.setLoginUrl(loginUrl);

        // ------------------------------ TX FAILURE COMPENSATION ------------------------------
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        keycloak.realm(req.getTenantName()).remove();
                    } catch (Exception ignored) {}
                }
            }
        });

        // ------------------------------ CREATE ADMIN USER IN REALM ------------------------------
        try {
            UserRepresentation kcUser = new UserRepresentation();
            kcUser.setUsername(req.getAdminUserName());
            kcUser.setEnabled(true);
            kcUser.setEmail(req.getAdminEmail());
            kcUser.setFirstName(req.getAdminFirstName());
            kcUser.setLastName(req.getAdminLastName());
            kcUser.setEmailVerified(false);

            Response createUserResp =
                    keycloak.realm(req.getTenantName()).users().create(kcUser);

            int status = createUserResp.getStatus();
            if (status != 201 && status != 409) {
                throw new KeycloakOperationException("USER_CREATION_FAILED", 1010,
                        "Unable to create tenant admin user.");
            }

            String createdUserId = CreatedResponseUtil.getCreatedId(createUserResp);
            createUserResp.close();

            UserResource userRes = keycloak.realm(req.getTenantName()).users().get(createdUserId);

            // required actions
            List<String> requiredActions = new ArrayList<>();
            requiredActions.add("UPDATE_PASSWORD");
            requiredActions.add("VERIFY_EMAIL");

            userRes.executeActionsEmail(requiredActions);

            savedUser.setKeycloakUserId(createdUserId);
            savedUser.setPassword(null);
            savedUser.setStatus("ACTIVE");
            userRepository.save(savedUser);

            savedTenant.setStatus("ACTIVE");
            tenantRepository.save(savedTenant);

            sendWelcomeEmail(
                    req.getAdminEmail(),
                    loginUrl,
                    req.getAdminUserName()
            );

            ClientRepresentation realmMgmtClient =
                    keycloak.realm(req.getTenantName())
                            .clients()
                            .findByClientId("realm-management")
                            .get(0);

            RoleRepresentation realmAdminRole =
                    keycloak.realm(req.getTenantName())
                            .clients()
                            .get(realmMgmtClient.getId())
                            .roles()
                            .get("realm-admin")
                            .toRepresentation();

            userRes.roles()
                    .clientLevel(realmMgmtClient.getId())
                    .add(List.of(realmAdminRole));

        } catch (Exception e) {
            throw new KeycloakOperationException("USER_CREATION_FAILED", 1010,
                    "Unable to create tenant admin user.");
        }

        // ------------------------------ RESPONSE ------------------------------
        TenantResponse resp = new TenantResponse();
        resp.setTenantID(savedTenant.getTenantID());
        resp.setTenantName(savedTenant.getTenantName());
        resp.setRealmName(savedTenant.getRealmName());
        resp.setDomain(savedTenant.getDomain());
        resp.setRegion(savedTenant.getRegion());
        resp.setPhoneNo(savedTenant.getPhoneNo());
        resp.setTenantType(savedTenant.getTenantType());
        resp.setIndustry(savedTenant.getIndustry());
        resp.setTemporaryAddress(savedTenant.getTemporaryAddress());
        resp.setPermanentAddress(savedTenant.getPermanentAddress());
        resp.setBillingAddress(savedTenant.getBillingAddress());
        resp.setStatus(savedTenant.getStatus());
        resp.setCreatedAt(Instant.now());
        resp.setLoginUrl(loginUrl);

        return resp;
    }

    // =============================================================================================
    // GET TENANT
    // =============================================================================================

    @Transactional(readOnly = true)
    public TenantResponse getTenant(String id) {
        Tenant t = tenantRepository.findByTenantID(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));
        TenantResponse resp = new TenantResponse();
        resp.setTenantID(t.getTenantID());
        resp.setTenantName(t.getTenantName());
        resp.setRealmName(t.getRealmName());
        resp.setDomain(t.getDomain());
        resp.setRegion(t.getRegion());
        resp.setPhoneNo(t.getPhoneNo());
        resp.setTemporaryAddress(t.getTemporaryAddress());
        resp.setPermanentAddress(t.getPermanentAddress());
        resp.setBillingAddress(t.getBillingAddress());
        resp.setStatus(t.getStatus());
        resp.setCreatedAt(t.getCreatedAt() == null ? Instant.now() : t.getCreatedAt());
        return resp;
    }

    // =============================================================================================
    // UPDATE TENANT
    // =============================================================================================

    @Transactional
    public TenantResponse updateTenant(String id, CreateTenantRequest req) {
        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));

        t.setTenantName(req.getTenantName());
        t.setDomain(req.getDomain());
        t.setRegion(req.getRegion());
        t.setPhoneNo(req.getPhoneNo());
        t.setTenantType(req.getTenantType());
        t.setIndustry(req.getIndustry());
        t.setTemporaryAddress(req.getTemporaryAddress());
        t.setPermanentAddress(req.getPermanentAddress());
        t.setBillingAddress(req.getBillingAddress());
        tenantRepository.save(t);

        TenantResponse resp = new TenantResponse();
        resp.setTenantID(t.getTenantID());
        resp.setTenantName(t.getTenantName());
        resp.setRealmName(t.getRealmName());
        resp.setStatus(t.getStatus());
        resp.setCreatedAt(t.getCreatedAt());
        return resp;
    }

    // =============================================================================================
    // DELETE TENANT
    // =============================================================================================

    @Transactional
    public void deleteTenant(String id) {
        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));

        String realm = t.getRealmName();

        try {
            keycloak.realm(realm).remove();
        } catch (Exception e) {
            throw new KeycloakOperationException("DELETE_REALM_FAILED", 1014,
                    "Unable to remove tenant environment.");
        }

        // delete all users of this tenant
        List<User> users = userRepository.findByTenant(t);
        userRepository.deleteAll(users);

        tenantRepository.delete(t);
    }

    // =============================================================================================
    // GET ACCESS TOKEN
    // =============================================================================================

    public Map<String,Object> getAccessToken(String code) {

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUrl);
        form.add("client_id", "secufusion");
        form.add("client_secret", "PuExdrzPcRCKS4aJgPcutpPuHMSQ0t0P");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(realmUrl, request, Map.class);

            return response.getBody();
        } catch (Exception e) {
            throw new KeycloakOperationException("TOKEN_GENERATION_FAILED", 1015,
                    "We could not process your login request at the moment.");
        }
    }

    // =============================================================================================
    // OTHER HELPERS
    // =============================================================================================

    public List<Tenant> getAllTenants() {
        return tenantRepository.findAll();
    }

    public List<TenantType> getAllTenantTypes() {
        return tenantTypeRepository.findAll();
    }

    public List<Map<String, Object>> getTenantBillingTypes() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(Map.of("id", 1, "billingType", "Trial"));
        list.add(Map.of("id", 2, "billingType", "Monthly"));
        list.add(Map.of("id", 3, "billingType", "Quarterly"));
        list.add(Map.of("id", 4, "billingType", "Yearly"));
        return list;
    }

    public void sendWelcomeEmail(String to, String loginUrl, String username) throws MessagingException {

        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.hostinger.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("no-reply@motivitylabs.net", "Motivity@net@123");
            }
        });

        Message msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress("no-reply@motivitylabs.net", false));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        msg.setSubject("Welcome to Secufusion");
        msg.setContent(
                "<h3>Welcome to Secufusion!</h3>" +
                        "<p>Your account has been created successfully.</p>" +
                        "<p><b>Login URL:</b> <a href='" + loginUrl + "'>" + loginUrl + "</a></p>" +
                        "<p><b>Username:</b> " + username + "</p>" +
                        "<p>First verify email and set password via the link you received.</p>",
                "text/html"
        );

        Transport.send(msg);
    }
}
