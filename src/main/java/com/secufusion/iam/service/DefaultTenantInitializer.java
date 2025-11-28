package com.secufusion.iam.service;

import com.secufusion.iam.dto.CreateTenantRequest;
import com.secufusion.iam.entity.Groups;
import com.secufusion.iam.entity.Roles;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.User;
import com.secufusion.iam.repository.GroupsRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultTenantInitializer {

    private final TenantRepository tenantRepository;
    private final TenantService tenantService;
    private final RoleService roleService;
    private final GroupService groupService;
    private final GroupsRepository groupRepository;
    private final UserRepository userRepository;

    public void initialize() {

        log.info("==============================================================");
        log.info(">>> [INIT] Starting Default Tenant Onboarding Process");
        log.info("==============================================================");

        try {
            final String defaultTenantName = "secufusion";

            // -----------------------------------------------------------
            // 1) FETCH OR CREATE TENANT
            // -----------------------------------------------------------
            log.info("[1] Checking if tenant '{}' exists...", defaultTenantName);

            Tenant tenant = tenantRepository.findByTenantName(defaultTenantName)
                    .orElse(null);

            if (tenant == null) {
                log.info("[1] Tenant '{}' NOT found → Creating new tenant...", defaultTenantName);

                CreateTenantRequest req = new CreateTenantRequest();
                req.setTenantName(defaultTenantName);
                req.setDomain("master.motivitylabs.net");
                req.setRegion("GLOBAL");
                req.setTenantType("Master MSSP");
                req.setIndustry("Technology");
                req.setPhoneNo("+91-0000000000");
                req.setBillingCycleType("Yearly");

                req.setAdminPhoneNumber("+91-1111111111");
                req.setAdminFirstName("Software");
                req.setAdminLastName("Admin");
                req.setAdminUserName("softwareadmin");
                req.setAdminEmail("no-reply@motivitylabs.net");

                tenantService.createTenant(req);

                tenant = tenantRepository.findByTenantName(defaultTenantName)
                        .orElseThrow(() -> new RuntimeException("Tenant creation failed !"));

                log.info("[1] Tenant '{}' successfully created with ID={}",
                        defaultTenantName, tenant.getTenantID());

            } else {
                log.info("[1] Tenant '{}' already exists (ID={})",
                        defaultTenantName, tenant.getTenantID());
            }

            final String tenantId = tenant.getTenantID();

            Optional<User> tenantUser = userRepository.findByTenant_TenantIDAndDefaultUser(tenantId, true);

            // -----------------------------------------------------------
            // 2) ENSURE DEFAULT ROLE EXISTS
            // -----------------------------------------------------------
            log.info("[2] Ensuring default roles for tenantId={} ...", tenantId);

            Roles adminRole = roleService.createOrGetDefaultRole(
                    tenantId, defaultTenantName + "_Admin", "Administrator role", tenantUser.get().getPkUserId());

            log.info("[2] Default roles verified (Admin)");

            // -----------------------------------------------------------
            // 3) ENSURE DEFAULT GROUP EXISTS
            // -----------------------------------------------------------
            log.info("[3] Checking default Admin group existence...");

            String expectedGroupName = defaultTenantName + "_Admin";

            Groups adminGroup = groupRepository
                    .findByTenant_TenantIDAndIsAdminAndIsDefault(tenantId, 'Y', 'Y')
                    .orElse(null);

            if (adminGroup == null) {
                log.warn("[3] No default admin group found → Creating '{}'", expectedGroupName);

                adminGroup = groupService.createOrGetDefaultGroup(
                        tenantId, expectedGroupName, true, tenantUser.get().getPkUserId());

                adminGroup.setIsDefault('Y');
                groupRepository.save(adminGroup);

                log.info("[3] Default admin group '{}' created (ID={})",
                        expectedGroupName, adminGroup.getPkGroupId());
            } else {
                log.info("[3] Default admin group already exists (ID={})",
                        adminGroup.getPkGroupId());
            }

            // -----------------------------------------------------------
            // 4) ASSIGN ROLES → GROUP
            // -----------------------------------------------------------
            log.info("[4] Ensuring Admin role is mapped to default admin group...");

            groupService.assignRoleToGroup(adminGroup, adminRole);

            log.info("[4] Admin role mapping complete.");

            // -----------------------------------------------------------
            // 5) ASSIGN DEFAULT ADMIN USER → GROUP
            // -----------------------------------------------------------
            log.info("[5] Checking for default admin user under tenant...");

            User defaultAdminUser = tenant.getUsers()
                    .stream()
                    .filter(User::isDefaultUser)
                    .findFirst()
                    .orElse(null);

            if (defaultAdminUser != null) {
                log.info("[5] Default admin user found (username={}) → mapping to group...",
                        defaultAdminUser.getUserName());

                groupService.assignUserToGroup(adminGroup, defaultAdminUser);

                log.info("[5] Default admin user mapped to group '{}'", expectedGroupName);
            } else {
                log.warn("[5] No default admin user found for tenant '{}'.", defaultTenantName);
            }

            // -----------------------------------------------------------
            log.info("==============================================================");
            log.info(">>> [INIT] Default Tenant Onboarding Completed Successfully");
            log.info("==============================================================");

        } catch (Exception ex) {
            log.error("==============================================================");
            log.error(">>> [INIT ERROR] Default Onboarding FAILED: {}", ex.getMessage(), ex);
            log.error("==============================================================");
        }
    }
}
