package com.secufusion.iam.service;

import com.secufusion.iam.dto.AuthDetailsDto;
import com.secufusion.iam.entity.AuthProviderConfig;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.AuthProviderConfigRepository;
import com.secufusion.iam.repository.TenantRepository;
import jakarta.persistence.Cacheable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

        return new AuthDetailsDto(
                tenant.getTenantID(),
                tenant.getTenantName(),
                tenant.getTenantName(),
                tenant.getTenantType(),
                cfg.getAuthServerUrl(),
                tenant.getRealmName(),
                cfg.getClientId(),
                cfg.getIssuerUri(),
                cfg.getJwkUri(),
                cfg.getTokenEndpoint(),
                tenant.getDomain(),
                tenant.getStatus()
        );
    }


    public Map<String, JwtDecoder> getJwtDecoders() {
        return authProviderConfigRepository.findAll().stream()
                .collect(Collectors.toMap(
                        AuthProviderConfig::getIssuerUri,
                        this::createJwtDecoder,
                        (existing, replacement) -> replacement  // Handle duplicates
                ));
    }

    private JwtDecoder createJwtDecoder(AuthProviderConfig config) {
        try {
            String jwkSetUri = config.getJwkUri() != null
                    ? config.getJwkUri()
                    : config.getIssuerUri() + "/protocol/openid-connect/certs";

            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        } catch (Exception e) {
            // Log error, skip invalid config
            log.warn("Failed to create JWT decoder for issuer {}: {}",
                    config.getIssuerUri(), e.getMessage());
            return null;
        }
    }
}
