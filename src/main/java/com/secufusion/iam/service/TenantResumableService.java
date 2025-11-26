package com.secufusion.iam.service;

import com.secufusion.iam.dto.CreateTenantRequest;
import com.secufusion.iam.dto.TenantResponse;
import com.secufusion.iam.entity.AuthProviderConfig;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.TenantType;
import com.secufusion.iam.entity.User;
import com.secufusion.iam.exception.KeycloakOperationException;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.AuthProviderConfigRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.TenantTypeRepository;
import com.secufusion.iam.repository.UserRepository;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.ws.rs.core.Response;

import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class TenantResumableService {

    private static final Logger log = LoggerFactory.getLogger(TenantResumableService.class);

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final Keycloak keycloak;

    @Autowired
    private AuthProviderConfigRepository authProviderConfigRepository;
    @Autowired
    private TenantTypeRepository tenantTypeRepository;

    @Value("${keycloak.admin.server-url}")
    private String baseUrl;

    @Value("${mail.smtp.host}")
    private String smtpHost;
    @Value("${mail.smtp.port}")
    private String smtpPort;
    @Value("${mail.smtp.auth}")
    private String smtpAuth;
    @Value("${mail.smtp.username}")
    private String smtpUsername;
    @Value("${mail.smtp.password}")
    private String smtpPassword;
    @Value("${mail.smtp.mail}")
    private String smtpMail;

    public TenantResumableService(TenantRepository tenantRepository,
                                  UserRepository userRepository,
                                  Keycloak keycloak) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.keycloak = keycloak;
    }

    // ============================================================================ VALIDATION

    private void validateInputForNew(CreateTenantRequest req) {
        log.debug("Validating input for new tenant. tenantName={}, adminUserName={}, adminEmail={}, domain={}",
                req.getTenantName(), req.getAdminUserName(), req.getAdminEmail(), req.getDomain());

        if (tenantRepository.findByTenantName(req.getTenantName()).isPresent()) {
            log.warn("Validation failed: tenant name already exists. tenantName={}", req.getTenantName());
            throw new KeycloakOperationException("TENANT_ALREADY_EXISTS", 1001, "Tenant name already exists.");
        }

        String dbDomain = normalizeDomainForDB(req.getDomain());
        boolean domainExists = tenantRepository.findAll().stream()
                .anyMatch(t -> dbDomain.equalsIgnoreCase(t.getDomain()));
        if (domainExists) {
            log.warn("Validation failed: domain already exists. normalizedDomain={}", dbDomain);
            throw new KeycloakOperationException("DOMAIN_ALREADY_EXISTS", 1002, "This domain is already registered.");
        }

        if (userRepository.findByUserName(req.getAdminUserName()).isPresent()) {
            log.warn("Validation failed: username already taken. username={}", req.getAdminUserName());
            throw new KeycloakOperationException("USERNAME_ALREADY_EXISTS", 1003, "Username already taken.");
        }

        if (userRepository.findByEmail(req.getAdminEmail()).isPresent()) {
            log.warn("Validation failed: email already exists. email={}", req.getAdminEmail());
            throw new KeycloakOperationException("EMAIL_ALREADY_EXISTS", 1004, "Email already exists.");
        }

        boolean realmExists = keycloak.realms().findAll().stream()
                .anyMatch(r -> r.getRealm().equalsIgnoreCase(req.getTenantName()));
        if (realmExists) {
            log.warn("Validation failed: realm already exists in Keycloak. realm={}", req.getTenantName());
            throw new KeycloakOperationException("REALM_ALREADY_EXISTS", 1005, "Realm already exists.");
        }

        log.debug("Validation for new tenant passed. tenantName={}", req.getTenantName());
    }

    // ============================================================================ CREATE TENANT

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest req) {
        log.info("Received request to create tenant. tenantName={}, domain={}, adminUserName={}, adminEmail={}",
                req.getTenantName(), req.getDomain(), req.getAdminUserName(), req.getAdminEmail());

        Optional<Tenant> existingOpt = tenantRepository.findByTenantName(req.getTenantName());
        boolean realmExists = keycloak.realms().findAll().stream()
                .anyMatch(r -> r.getRealm().equalsIgnoreCase(req.getTenantName()));

        log.debug("Existing tenant present={}, realmExistsInKeycloak={}",
                existingOpt.isPresent(), realmExists);

        if (existingOpt.isPresent()) {
            Tenant existing = existingOpt.get();
            log.info("Tenant already exists in DB. tenantId={}, status={}",
                    existing.getTenantID(), existing.getStatus());

            if ("ACTIVE".equalsIgnoreCase(existing.getStatus())) {
                log.warn("Attempt to create an already active tenant. tenantId={}, tenantName={}",
                        existing.getTenantID(), existing.getTenantName());
                throw new KeycloakOperationException("TENANT_ALREADY_ACTIVE", 1015, "Tenant already active.");
            }

            log.info("Resuming tenant setup for existing tenant. tenantId={}, status={}",
                    existing.getTenantID(), existing.getStatus());
            return resumeTenantSetup(req);
        }

        validateInputForNew(req);

        Tenant tenant = buildTenantSkeleton(req);
        tenant.setStatus("CREATING");
        Tenant savedTenant = tenantRepository.save(tenant);
        log.info("Created tenant skeleton in DB. tenantId={}, status={}",
                savedTenant.getTenantID(), savedTenant.getStatus());

        User admin = buildAdminSkeleton(req, savedTenant);
        admin.setStatus("CREATING");
        User savedUser = userRepository.save(admin);
        log.info("Created admin user skeleton in DB. userId={}, username={}, status={}",
                savedUser.getUserID(), savedUser.getUserName(), savedUser.getStatus());

        savedTenant.setUsers(new ArrayList<>(List.of(savedUser)));
        savedTenant.setStatus("CREATED_LOCAL");
        tenantRepository.save(savedTenant);
        log.info("Updated tenant status to CREATED_LOCAL. tenantId={}", savedTenant.getTenantID());

        return resumeTenantSetup(req);
    }

    private Tenant buildTenantSkeleton(CreateTenantRequest req) {
        log.debug("Building tenant skeleton for tenantName={}", req.getTenantName());
        Tenant tenant = new Tenant();
        tenant.setTenantName(req.getTenantName());
        tenant.setRealmName(req.getTenantName());
        tenant.setDomain(normalizeDomainForDB(req.getDomain()));
        tenant.setRegion(req.getRegion());
        tenant.setPhoneNo(req.getPhoneNo());
        tenant.setTenantType(req.getTenantType());
        tenant.setIndustry(req.getIndustry());
        tenant.setTemporaryAddress(req.getTemporaryAddress());
        tenant.setPermanentAddress(req.getPermanentAddress());
        tenant.setBillingAddress(req.getBillingAddress());
        tenant.setBillingCycleType(req.getBillingCycleType());
        return tenant;
    }

    private User buildAdminSkeleton(CreateTenantRequest req, Tenant tenant) {
        log.debug("Building admin skeleton for tenantName={}, adminUserName={}",
                req.getTenantName(), req.getAdminUserName());
        User admin = new User();
        admin.setFirstName(req.getAdminFirstName());
        admin.setLastName(req.getAdminLastName());
        admin.setUserName(req.getAdminUserName());
        admin.setEmail(req.getAdminEmail());
        admin.setPhoneNo(req.getPhoneNo());
        admin.setTenant(tenant);
        admin.setDefaultUser(true);
        return admin;
    }

    // ============================================================================ RESUME FLOW

    @Transactional
    public TenantResponse resumeTenantSetup(CreateTenantRequest req) {
        log.info("Resuming tenant setup. tenantName={}", req.getTenantName());

        Tenant tenant = tenantRepository.findByTenantName(req.getTenantName())
                .orElseThrow(() -> {
                    log.error("Tenant not found while resuming setup. tenantName={}", req.getTenantName());
                    return new KeycloakOperationException("TENANT_NOT_FOUND", 1014, "Tenant not found.");
                });

        log.info("Current tenant status={} for tenantId={}", tenant.getStatus(), tenant.getTenantID());

        return switch (tenant.getStatus()) {
            case "CREATING", "CREATED_LOCAL" -> {
                log.info("Starting full setup from REALM for tenantId={}", tenant.getTenantID());
                createRealm(tenant, req);
                createClient(tenant, req);
                createKeycloakAdminUser(tenant, req);
                finalizeAndSendEmails(tenant, req);
                saveAuthProviderConfig(tenant, req);
                yield buildResponse(tenant);
            }
            case "REALM_CREATED" -> {
                log.info("Resuming from CLIENT creation for tenantId={}", tenant.getTenantID());
                createClient(tenant, req);
                createKeycloakAdminUser(tenant, req);
                finalizeAndSendEmails(tenant, req);
                saveAuthProviderConfig(tenant, req);
                yield buildResponse(tenant);
            }
            case "CLIENT_CREATED" -> {
                log.info("Resuming from USER creation for tenantId={}", tenant.getTenantID());
                createKeycloakAdminUser(tenant, req);
                finalizeAndSendEmails(tenant, req);
                saveAuthProviderConfig(tenant, req);
                yield buildResponse(tenant);
            }
            case "USER_CREATED" -> {
                log.info("Resuming from FINALIZE/EMAIL step for tenantId={}", tenant.getTenantID());
                finalizeAndSendEmails(tenant, req);
                saveAuthProviderConfig(tenant, req);
                yield buildResponse(tenant);
            }
            case "ACTIVE" -> {
                log.info("Tenant already ACTIVE, returning existing response. tenantId={}", tenant.getTenantID());
                yield buildResponse(tenant);
            }
            default -> {
                log.error("Unknown provisioning state for tenantId={}, status={}",
                        tenant.getTenantID(), tenant.getStatus());
                throw new KeycloakOperationException("UNKNOWN_STATE", 1013,
                        "Invalid provisioning state.");
            }
        };
    }

    // ============================================================================ REALM

    private void createRealm(Tenant tenant, CreateTenantRequest req) {
        log.info("Creating Keycloak realm if not exists. realm={}", req.getTenantName());

        boolean existsBefore = keycloak.realms().findAll().stream()
                .anyMatch(r -> r.getRealm().equalsIgnoreCase(req.getTenantName()));

        if (!existsBefore) {
            log.debug("Realm does not exist, creating new realm. realm={}", req.getTenantName());

            RealmRepresentation realmRep = new RealmRepresentation();
            realmRep.setRealm(req.getTenantName());
            realmRep.setEnabled(true);
            realmRep.setSmtpServer(getSmtpConfig());

            keycloak.realms().create(realmRep);
            log.info("Realm created in Keycloak. realm={}", req.getTenantName());
        } else {
            log.info("Realm already exists in Keycloak, skipping creation. realm={}", req.getTenantName());
        }

        tenant.setStatus("REALM_CREATED");
        tenantRepository.save(tenant);
        log.info("Tenant status updated to REALM_CREATED. tenantId={}", tenant.getTenantID());
    }

    // ============================================================================ CLIENT

    private void createClient(Tenant tenant, CreateTenantRequest req) {
        log.info("Creating Keycloak client if not exists. realm={}, clientId={}",
                req.getTenantName(), req.getTenantName());

        boolean existsBefore = keycloak.realm(req.getTenantName())
                .clients().findAll().stream()
                .anyMatch(c -> c.getClientId().equalsIgnoreCase(req.getTenantName()));

        if (!existsBefore) {
            String redirectUri = normalizeDomainForRedirect(req.getDomain());
            log.debug("Client does not exist, creating new client. clientId={}, redirectUri={}",
                    req.getTenantName(), redirectUri);

            ClientRepresentation clientRep = new ClientRepresentation();
            clientRep.setClientId(req.getTenantName());
            clientRep.setName(req.getTenantName());
            clientRep.setProtocol("openid-connect");
            clientRep.setPublicClient(true);
            clientRep.setRedirectUris(List.of(redirectUri));
            clientRep.setWebOrigins(List.of("*"));
            clientRep.setStandardFlowEnabled(true);
            clientRep.setEnabled(true);

            Response resp = keycloak.realm(req.getTenantName()).clients().create(clientRep);
            log.debug("Keycloak client creation response status={}", resp.getStatus());
            resp.close();

            log.info("Client created in Keycloak. clientId={}, realm={}",
                    req.getTenantName(), req.getTenantName());
        } else {
            log.info("Client already exists in Keycloak, skipping creation. clientId={}, realm={}",
                    req.getTenantName(), req.getTenantName());
        }

        tenant.setStatus("CLIENT_CREATED");
        tenantRepository.save(tenant);
        log.info("Tenant status updated to CLIENT_CREATED. tenantId={}", tenant.getTenantID());
    }

    // ============================================================================ USER

    private void createKeycloakAdminUser(Tenant tenant, CreateTenantRequest req) {
        log.info("Creating Keycloak admin user if not exists. realm={}, username={}",
                req.getTenantName(), req.getAdminUserName());

        List<User> localUsers = tenant.getUsers();
        User admin = localUsers.stream().filter(User::isDefaultUser).findFirst().orElseThrow(() -> {
            log.error("Default admin user not found in local DB for tenantId={}", tenant.getTenantID());
            return new IllegalStateException("Default admin user not found.");
        });

        List<UserRepresentation> existing =
                keycloak.realm(req.getTenantName()).users().search(req.getAdminUserName(), true);

        if (!existing.isEmpty()) {
            String existingUserId = existing.get(0).getId();
            log.info("Keycloak user already exists, linking to local admin. realm={}, username={}, keycloakUserId={}",
                    req.getTenantName(), req.getAdminUserName(), existingUserId);

            admin.setKeycloakUserId(existingUserId);
            admin.setStatus("ACTIVE");
            userRepository.save(admin);

            tenant.setStatus("USER_CREATED");
            tenantRepository.save(tenant);
            log.info("Tenant status updated to USER_CREATED (existing user). tenantId={}", tenant.getTenantID());
            return;
        }

        log.debug("Keycloak admin user does not exist, creating new user. realm={}, username={}",
                req.getTenantName(), req.getAdminUserName());

        UserRepresentation user = new UserRepresentation();
        user.setUsername(req.getAdminUserName());
        user.setEmail(req.getAdminEmail());
        user.setFirstName(req.getAdminFirstName());
        user.setLastName(req.getAdminLastName());
        user.setEnabled(true);

        Response response = keycloak.realm(req.getTenantName()).users().create(user);
        String userId = CreatedResponseUtil.getCreatedId(response);
        log.debug("Keycloak user creation response status={}, keycloakUserId={}",
                response.getStatus(), userId);
        response.close();

        UserResource userRes = keycloak.realm(req.getTenantName()).users().get(userId);
        log.debug("Triggering executeActionsEmail for user. keycloakUserId={}", userId);
        userRes.executeActionsEmail(List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));

        ClientRepresentation realmMgmtClient =
                keycloak.realm(req.getTenantName()).clients().findByClientId("realm-management").get(0);

        RoleRepresentation realmAdminRole =
                keycloak.realm(req.getTenantName())
                        .clients().get(realmMgmtClient.getId())
                        .roles().get("realm-admin").toRepresentation();

        log.debug("Assigning realm-admin role to user. keycloakUserId={}, clientId={}",
                userId, realmMgmtClient.getId());
        userRes.roles().clientLevel(realmMgmtClient.getId()).add(List.of(realmAdminRole));

        admin.setKeycloakUserId(userId);
        admin.setStatus("ACTIVE");
        userRepository.save(admin);

        tenant.setStatus("USER_CREATED");
        tenantRepository.save(tenant);
        log.info("Tenant status updated to USER_CREATED (new user). tenantId={}", tenant.getTenantID());
    }

    // ============================================================================ FINALIZE

    private void finalizeAndSendEmails(Tenant tenant, CreateTenantRequest req) {
        log.info("Finalizing tenant setup and sending welcome email. tenantId={}, adminEmail={}",
                tenant.getTenantID(), req.getAdminEmail());
        try {
            String loginUrl = generateLoginUrl(req);
            log.debug("Generated login URL for tenantName={}: {}", req.getTenantName(), loginUrl);
            sendWelcomeEmail(req.getAdminEmail(), loginUrl, req.getAdminUserName());
            tenant.setStatus("ACTIVE");
            tenantRepository.save(tenant);
            log.info("Tenant activated and welcome email sent successfully. tenantId={}", tenant.getTenantID());
        } catch (Exception e) {
            log.error("Failed to send welcome email or finalize tenant. tenantId={}, adminEmail={}, error={}",
                    tenant.getTenantID(), req.getAdminEmail(), e.getMessage(), e);
            throw new KeycloakOperationException("EMAIL_SEND_FAILED", 1011,
                    "Unable to send welcome email.", e);
        }
    }

    // ============================================================================ SAVE AUTH CONFIG

    private void saveAuthProviderConfig(Tenant tenant, CreateTenantRequest req) {
        log.info("Saving auth provider config if not exists. tenantId={}", tenant.getTenantID());

        if (authProviderConfigRepository.findByTenant(tenant).isPresent()) {
            log.info("Auth provider config already exists for tenantId={}, skipping.", tenant.getTenantID());
            return;
        }

        String redirectUri = normalizeDomainForRedirect(req.getDomain());
        String loginUrl = generateLoginUrl(req);
        log.debug("Using redirectUri={} and loginUrl={} for auth config. tenantId={}",
                redirectUri, loginUrl, tenant.getTenantID());

        AuthProviderConfig cfg = new AuthProviderConfig();
        cfg.setTenant(tenant);
        cfg.setSsoType("KEYCLOAK");
        cfg.setIssuerUri(baseUrl + "/realms/" + tenant.getRealmName());
        cfg.setAuthServerUrl(baseUrl);
        cfg.setTokenEndpoint(baseUrl + "/realms/" + tenant.getRealmName() + "/protocol/openid-connect/token");
        cfg.setJwkUri(baseUrl + "/realms/" + tenant.getRealmName() + "/protocol/openid-connect/certs");
        cfg.setClientId(tenant.getTenantName());
        cfg.setRedirectUri(redirectUri);
        cfg.setLoginUrl(loginUrl);
        cfg.setScopes("openid profile email");

        authProviderConfigRepository.save(cfg);
        log.info("Auth provider config saved successfully. tenantId={}", tenant.getTenantID());
    }

    // ============================================================================ MAIL

    public void sendWelcomeEmail(String to, String loginUrl, String username) throws MessagingException {
        log.info("Sending welcome email. to={}, username={}", to, username);

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", smtpAuth);
        props.put("mail.smtp.starttls.enable", "true");

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
        log.info("Welcome email sent successfully. to={}", to);
    }

    // ============================================================================ HELPERS

    private Map<String, String> getSmtpConfig() {
        log.debug("Building SMTP configuration map.");
        Map<String, String> smtp = new HashMap<>();
        smtp.put("host", smtpHost);
        smtp.put("port", smtpPort);
        smtp.put("from", smtpMail);
        smtp.put("user", smtpUsername);
        smtp.put("password", smtpPassword);
        smtp.put("auth", smtpAuth);
        smtp.put("starttls", "true");
        smtp.put("ssl", "false");
        return smtp;
    }

    // DB FORMAT: support.motivitylabs.net
    private String normalizeDomainForDB(String domain) {
        log.debug("Normalizing domain for DB. rawDomain={}", domain);

        if (domain == null || domain.trim().isBlank()) {
            log.warn("Invalid domain provided while normalizing for DB. domain={}", domain);
            throw new KeycloakOperationException("INVALID_DOMAIN", 1007, "A valid domain must be provided.");
        }

        String d = domain.trim().toLowerCase();

        if (d.startsWith("http://")) d = d.substring(7);
        if (d.startsWith("https://")) d = d.substring(8);

        if (d.contains(".motivitylabs.net")) {
            log.debug("Domain already contains .motivitylabs.net. normalizedDomain={}", d);
            return d;
        }

        if (!d.contains(".")) {
            String normalized = d + ".motivitylabs.net";
            log.debug("Domain has no dot, appending default suffix. normalizedDomain={}", normalized);
            return normalized;
        }

        String normalized = d.split("\\.")[0] + ".motivitylabs.net";
        log.debug("Domain normalized to subdomain.motivitylabs.net. normalizedDomain={}", normalized);
        return normalized;
    }

    // CLIENT FORMAT: https://support.motivitylabs.net/*
    private String normalizeDomainForRedirect(String domain) {
        String redirect = "https://" + normalizeDomainForDB(domain) + "/*";
        log.debug("Normalized domain for redirect. rawDomain={}, redirectUri={}", domain, redirect);
        return redirect;
    }

    private String generateLoginUrl(CreateTenantRequest req) {
        String redirect = "https://" + normalizeDomainForDB(req.getDomain());
        String loginUrl = baseUrl + "/realms/" + req.getTenantName()
                + "/protocol/openid-connect/auth?client_id=" + req.getTenantName()
                + "&redirect_uri=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8)
                + "&response_type=code";
        log.debug("Generated login URL. tenantName={}, loginUrl={}", req.getTenantName(), loginUrl);
        return loginUrl;
    }

    private TenantResponse buildResponse(Tenant tenant) {
        log.debug("Building TenantResponse for tenantId={}, status={}",
                tenant.getTenantID(), tenant.getStatus());
        TenantResponse resp = new TenantResponse();
        resp.setTenantID(tenant.getTenantID());
        resp.setTenantName(tenant.getTenantName());
        resp.setRealmName(tenant.getRealmName());
        resp.setDomain(tenant.getDomain());
        resp.setRegion(tenant.getRegion());
        resp.setPhoneNo(tenant.getPhoneNo());
        resp.setStatus(tenant.getStatus());
        resp.setCreatedAt(tenant.getCreatedAt() != null ? tenant.getCreatedAt() : Instant.now());
        return resp;
    }

    // ============================================================================ CRUD

    @Transactional(readOnly = true)
    public TenantResponse getTenant(String id) {
        log.info("Fetching tenant by ID. tenantId={}", id);
        Tenant t = tenantRepository.findByTenantID(id)
                .orElseThrow(() -> {
                    log.warn("Tenant not found by ID. tenantId={}", id);
                    return new ResourceNotFoundException("Tenant not found: " + id);
                });
        return buildResponse(t);
    }

    @Transactional(readOnly = true)
    public List<Tenant> getAllTenants() {
        log.info("Fetching all tenants.");
        List<Tenant> tenants = tenantRepository.findAll();
        log.debug("Fetched {} tenants from DB.", tenants.size());
        return tenants;
    }

    @Transactional
    public TenantResponse updateTenant(String id, CreateTenantRequest req) {
        log.info("Updating tenant. tenantId={}, newTenantName={}", id, req.getTenantName());
        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Tenant not found for update. tenantId={}", id);
                    return new ResourceNotFoundException("Tenant not found: " + id);
                });

        t.setTenantName(req.getTenantName());
        t.setRealmName(req.getTenantName());
        t.setDomain(normalizeDomainForDB(req.getDomain()));
        t.setRegion(req.getRegion());
        t.setPhoneNo(req.getPhoneNo());
        t.setTenantType(req.getTenantType());
        t.setIndustry(req.getIndustry());
        t.setTemporaryAddress(req.getTemporaryAddress());
        t.setPermanentAddress(req.getPermanentAddress());
        t.setBillingAddress(req.getBillingAddress());
        t.setBillingCycleType(req.getBillingCycleType());

        tenantRepository.save(t);
        log.info("Tenant updated successfully. tenantId={}", id);
        return buildResponse(t);
    }

    @Transactional
    public void deleteTenant(String id) {
        log.info("Deleting tenant. tenantId={}", id);

        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Tenant not found for delete. tenantId={}", id);
                    return new ResourceNotFoundException("Tenant not found: " + id);
                });

        // Delete Keycloak realm
        try {
            log.info("Attempting to delete Keycloak realm. realmName={}", t.getRealmName());
            keycloak.realm(t.getRealmName()).remove();
            log.info("Keycloak realm deleted successfully. realmName={}", t.getRealmName());
        } catch (Exception e) {
            log.warn("Failed to delete Keycloak realm, continuing with local delete. realmName={}, error={}",
                    t.getRealmName(), e.getMessage(), e);
        }

        // Delete users
        List<User> users = userRepository.findByTenant(t);
        log.debug("Deleting {} users for tenantId={}", users.size(), id);
        userRepository.deleteAll(users);

        // Delete tenant
        tenantRepository.delete(t);
        log.info("Tenant deleted successfully from DB. tenantId={}", id);
    }

    @Transactional(readOnly = true)
    public List<TenantType> getAllTenantTypes() {
        log.info("Fetching all tenant types.");
        List<TenantType> types = tenantTypeRepository.findAll();
        log.debug("Fetched {} tenant types from DB.", types.size());
        return types;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTenantBillingTypes() {
        log.info("Fetching static tenant billing types.");
        List<Map<String, Object>> billingTypes = List.of(
                Map.of("id", 1, "billingType", "Trial"),
                Map.of("id", 2, "billingType", "Monthly"),
                Map.of("id", 3, "billingType", "Quarterly"),
                Map.of("id", 4, "billingType", "Yearly")
        );
        log.debug("Returning {} billing types.", billingTypes.size());
        return billingTypes;
    }
}
