package com.secufusion.iam.service;

import com.secufusion.iam.dto.UsersDto;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.User;
import com.secufusion.iam.exception.KeycloakOperationException;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.UserRepository;
import com.secufusion.iam.util.KeycloakAdminUtil;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserService {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private KeycloakAdminUtil kcUtil;

    // ========================================================================
    // CREATE USER
    // ========================================================================
    @Transactional
    public UsersDto createUser(String tenantId, UsersDto dto) {

        log.info("➡️ [CREATE USER] Start. tenantId={}, dto={}", tenantId, dto);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> {
                    log.error("❌ Tenant not found while creating user. tenantId={}", tenantId);
                    return new ResourceNotFoundException("Tenant not found: " + tenantId);
                });

        log.debug("Tenant found: realm={}, tenantId={}", tenant.getRealmName(), tenant.getTenantID());

        // VALIDATE (new user → excludeUserId=null)
        validateUserFields(tenant, dto, null);

        try {
            log.debug("Creating local DB user record...");

            User user = new User();
            user.setFirstName(dto.getFirstName());
            user.setLastName(dto.getLastName());
            user.setEmail(dto.getEmail());
            user.setUserName(dto.getUserName());
            user.setPhoneNo(dto.getPhoneNumber());
            user.setTenant(tenant);
            user.setStatus("CREATING");

            User savedUser = userRepository.save(user);
            log.info("✔ Local user created successfully. userId={}", savedUser.getPkUserId());

            log.debug("Creating user in Keycloak. realm={}, username={}",
                    tenant.getRealmName(), dto.getUserName());

            String kcUserId = kcUtil.createUser(
                    tenant.getRealmName(),
                    dto.getUserName(),
                    dto.getEmail(),
                    dto.getFirstName(),
                    dto.getLastName()
            );

            if (kcUserId == null) {
                log.error("❌ Keycloak returned null userId. Username may already exist.");
                throw new KeycloakOperationException(
                        "KC_USER_CREATION_FAILED", 3001,
                        "Failed to create user in Keycloak (duplicate?)"
                );
            }

            log.info("✔ Keycloak user created. kcUserId={}", kcUserId);

            // Send required actions
            kcUtil.sendRequiredActionEmail(
                    tenant.getRealmName(),
                    kcUserId,
                    List.of("UPDATE_PASSWORD", "VERIFY_EMAIL")
            );

            kcUtil.sendWelcomeEmail(savedUser.getEmail(), tenant.getAuthProviderConfig().getLoginUrl(), savedUser.getUserName());

            log.info("✔ Required action email triggered for kcUserId={}", kcUserId);

            // Assign realm-admin role (or default roles)
            kcUtil.assignRealmAdminRole(tenant.getRealmName(), kcUserId);

            log.info("✔ Assigned realm-admin role to kcUserId={}", kcUserId);

            // Update DB with KC ID
            savedUser.setKeycloakUserId(kcUserId);
            savedUser.setStatus("ACTIVE");
            userRepository.save(savedUser);

            log.info("🎉 User successfully created in DB + KC. userId={} kcUserId={}",
                    savedUser.getPkUserId(), kcUserId);

            return mapToDto(savedUser);

        } catch (KeycloakOperationException ex) {
            log.error("❌ KeycloakOperationException during user creation: {}", ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            log.error("❌ Unexpected error while creating user {}: {}",
                    dto.getUserName(), ex.getMessage(), ex);

            throw new KeycloakOperationException(
                    "USER_CREATION_FAILED", 3002,
                    "Unexpected error while creating user"
            );
        }
    }


    // ========================================================================
    // UPDATE USER
    // ========================================================================
    @Transactional
    public UsersDto updateUser(String userId, UsersDto dto) {

        log.info("➡️ [UPDATE USER] userId={}, dto={}", userId, dto);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("❌ Cannot update user. User not found: {}", userId);
                    return new ResourceNotFoundException("User not found: " + userId);
                });

        Tenant tenant = user.getTenant();
        log.debug("User found. tenantId={}, realm={}", tenant.getTenantID(), tenant.getRealmName());

        validateUserFields(tenant, dto, userId);

        try {
            log.debug("Updating local DB user...");

            user.setFirstName(dto.getFirstName());
            user.setLastName(dto.getLastName());
            user.setEmail(dto.getEmail());
            user.setUserName(dto.getUserName());
            user.setPhoneNo(dto.getPhoneNumber());
            userRepository.save(user);

            log.info("✔ Local DB user updated. userId={}", userId);

            kcUtil.updateUser(
                    tenant.getRealmName(),
                    user.getKeycloakUserId(),
                    dto.getUserName(),
                    dto.getEmail(),
                    dto.getFirstName(),
                    dto.getLastName()
            );

            log.info("✔ Keycloak user updated. kcUserId={}", user.getKeycloakUserId());

            return mapToDto(user);

        } catch (KeycloakOperationException ex) {
            log.error("❌ KC failure during update: {}", ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            log.error("❌ Unexpected error updating user {}: {}", userId, ex.getMessage(), ex);

            throw new KeycloakOperationException(
                    "USER_UPDATE_FAILED", 3003,
                    "Unexpected error updating user"
            );
        }
    }


    // ========================================================================
    // DELETE USER
    // ========================================================================
    @Transactional
    public boolean deleteUser(String id) {

        log.info("➡️ [DELETE USER] userId={}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("❌ Cannot delete. User not found: {}", id);
                    return new ResourceNotFoundException("User not found: " + id);
                });

        boolean kcDeleted = false;

        // Delete from Keycloak
        try {
            log.debug("Deleting user from Keycloak. realm={}, kcUserId={}",
                    user.getTenant().getRealmName(), user.getKeycloakUserId());

            kcUtil.removeUser(user.getTenant().getRealmName(), user.getKeycloakUserId());
            kcDeleted = true;

            log.info("✔ Deleted user from Keycloak. userId={}", id);

        } catch (Exception e) {
            log.error("⚠️ Failed to delete user {} from Keycloak: {}", id, e.getMessage());
        }

        // Delete from DB
        try {
            log.debug("Deleting user from DB...");
            userRepository.delete(user);
            log.info("✔ Deleted user from DB. userId={}", id);

        } catch (Exception e) {
            log.error("❌ Failed to delete user {} from DB: {}", id, e.getMessage());
            return false;
        }

        return kcDeleted;
    }


    // ========================================================================
    // GET USER
    // ========================================================================
    public UsersDto getUser(String id) {
        log.info("➡️ [GET USER] userId={}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("❌ User not found: {}", id);
                    return new ResourceNotFoundException("User not found");
                });

        log.info("✔ User fetched: {}", id);
        return mapToDto(user);
    }


    // ========================================================================
    // GET ALL USERS
    // ========================================================================
    public List<UsersDto> getAllUsers() {
        log.info("➡️ [GET ALL USERS]");

        List<UsersDto> list = userRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        log.info("✔ Total users fetched={}", list.size());
        return list;
    }


    // ========================================================================
    // GET USERS BY TENANT
    // ========================================================================
    public List<UsersDto> getUsersByTenantId(String tenantId) {

        log.info("➡️ [GET USERS BY TENANT] tenantId={}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        List<UsersDto> list = userRepository.findByTenant(tenant)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        log.info("✔ Users fetched for tenantId={} count={}", tenantId, list.size());
        return list;
    }


    // ========================================================================
    // VALIDATION (DB + KEYCLOAK)
    // ========================================================================
    private void validateUserFields(Tenant tenant, UsersDto dto, String excludeUserId) {

        String tenantId = tenant.getTenantID();
        String realm = tenant.getRealmName();

        log.info("➡️ [VALIDATE USER] tenantId={} realm={} excludeUserId={}",
                tenantId, realm, excludeUserId);

        // ----- DB UNIQUE CHECKS -----
        log.debug("Checking DB uniqueness...");

        // EMAIL
        userRepository.findByEmailAndTenant_TenantID(dto.getEmail(), tenantId)
                .ifPresent(existing -> {
                    if (!existing.getPkUserId().equals(excludeUserId)) {
                        log.error("❌ Email already exists in tenant: {}", dto.getEmail());
                        throw new KeycloakOperationException(
                                "EMAIL_EXISTS", 3101, "Email already exists in this tenant");
                    }
                });

        // USERNAME
        userRepository.findByUserNameAndTenant_TenantID(dto.getUserName(), tenantId)
                .ifPresent(existing -> {
                    if (!existing.getPkUserId().equals(excludeUserId)) {
                        log.error("❌ Username already exists: {}", dto.getUserName());
                        throw new KeycloakOperationException(
                                "USERNAME_EXISTS", 3102, "Username already exists in this tenant");
                    }
                });

        // PHONE
        userRepository.findByPhoneNoAndTenant_TenantID(dto.getPhoneNumber(), tenantId)
                .ifPresent(existing -> {
                    if (!existing.getPkUserId().equals(excludeUserId)) {
                        log.error("❌ Phone already exists: {}", dto.getPhoneNumber());
                        throw new KeycloakOperationException(
                                "PHONE_EXISTS", 3103, "Phone number already exists in this tenant");
                    }
                });


        // ----- KEYCLOAK UNIQUE CHECK -----
        log.debug("Checking Keycloak realm uniqueness...");

        List<UserRepresentation> kcUsers =
                kcUtil.findUsersByUsernameOrEmail(
                        realm,
                        dto.getUserName(),
                        dto.getEmail()
                );

        log.debug("KC search result count={} realm={}", kcUsers.size(), realm);

        // If updating → exclude the same KC record
        String excludeKcId = null;
        if (excludeUserId != null) {
            excludeKcId = userRepository.findById(excludeUserId)
                    .map(User::getKeycloakUserId)
                    .orElse(null);
        }

        for (UserRepresentation kc : kcUsers) {
            if (excludeKcId != null && kc.getId().equals(excludeKcId)) {
                log.debug("Skipping KC user {} (same as updating user)", excludeKcId);
                continue;
            }

            log.error("❌ KC user conflict detected: username={} email={}",
                    dto.getUserName(), dto.getEmail());

            throw new KeycloakOperationException(
                    "KC_USER_EXISTS", 3104,
                    "User already exists in Keycloak realm"
            );
        }

        log.info("✔ Validation passed for user={} realm={}", dto.getUserName(), realm);
    }


    // ========================================================================
    // DTO MAPPER
    // ========================================================================
    private UsersDto mapToDto(User user) {
        log.debug("Mapping User -> UsersDto. userId={}", user.getPkUserId());
        UsersDto dto = new UsersDto();
        dto.setPkUserId(user.getPkUserId());
        dto.setUserName(user.getUserName());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPhoneNumber(user.getPhoneNo());
        return dto;
    }

    public String checkMobileNumber(String mobileNumber){
        boolean check =userRepository.existsByPhoneNo(mobileNumber);

        if (check){
            return "Mobile Number already exists.";
        } else{
            return "Mobile Number is available.";
        }
    }
    public String checkEmail(String email){
        boolean check =userRepository.existsByEmail(email);
        if (check){
            return "Email already exists.";
        } else{
            return "Email is available.";
        }
    }

    public String checkUserName(String userName) {
        boolean check = userRepository.existsByUserName(userName);
        if(check){
            return "Username already exists.";
        }else {
            return "Username is available.";
        }
    }
}
