package com.secufusion.iam.service;

import com.secufusion.iam.dto.AuthDetailsDto;
import com.secufusion.iam.entity.AuthProviderConfig;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.AuthProviderConfigRepository;
import com.secufusion.iam.repository.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AuthConfigService {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AuthProviderConfigRepository authProviderConfigRepository;

    public AuthDetailsDto getTenantConfig(String host) {

        log.info("Fetching tenant config for host={}", host);

        Tenant tenant = tenantRepository.findByDomain(host)
                .orElseGet(() -> tenantRepository.findByTenantName(host)
                        .orElseThrow(() -> new ResourceNotFoundException("Tenant not found for: " + host)));


        AuthProviderConfig cfg = authProviderConfigRepository.findByTenant(tenant)
                .orElseThrow(() -> new ResourceNotFoundException("Auth provider config missing"));

        String realm = tenant.getRealmName();

        return new AuthDetailsDto(
                tenant.getTenantID(),
                tenant.getTenantName(),
                tenant.getTenantName(),
                tenant.getTenantType(),
                cfg.getAuthServerUrl(),
                tenant.getRealmName(),
                cfg.getClientId(),
                cfg.getIssuerUri(),
                tenant.getDomain(),
                tenant.getStatus()
        );
    }
}
