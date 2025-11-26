package com.secufusion.iam.service;

import com.secufusion.iam.dto.CreateTenantRequest;
import com.secufusion.iam.repository.TenantRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultTenantInitializer {

    private final TenantRepository tenantRepository;
    private final TenantService tenantService;

    @PostConstruct
    public void initDefaultTenant() {
        try {
            String defaultTenant = "secufusion";

            log.info(">>> Checking default onboarding tenant existence: {}", defaultTenant);

            if (tenantRepository.findByTenantName(defaultTenant).isPresent()) {
                log.info(">>> Default Tenant '{}' already exists. Skipping initialization.", defaultTenant);
                return;
            }

            log.info(">>> Default Tenant '{}' not found. Creating default tenant and Keycloak realm...", defaultTenant);

            CreateTenantRequest req = new CreateTenantRequest();
            req.setTenantName("secufusion");
            req.setDomain("master.motivitylabs.net");
            req.setRegion("GLOBAL");
            req.setTenantType("Master MSSP");
            req.setIndustry("Technology");
            req.setPhoneNo("+91-0000000000");
            req.setBillingCycleType("Yearly");

            req.setAdminFirstName("Software");
            req.setAdminLastName("Admin");
            req.setAdminUserName("softwareadmin");
            req.setAdminEmail("no-reply@motivitylabs.net");
            req.setAdminPassword("Admin@123");

            tenantService.createTenant(req);

            log.info(">>> Default tenant '{}' successfully initialized and ACTIVE.", defaultTenant);

        } catch (Exception ex) {
            log.error(">>> ERROR: Failed to initialize default tenant: {}", ex.getMessage(), ex);
        }
    }
}