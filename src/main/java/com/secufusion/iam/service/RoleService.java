package com.secufusion.iam.service;

import com.secufusion.iam.entity.Roles;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.RolesRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class RoleService {

    @Autowired
    private RolesRepository rolesRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    /** Create or return existing */
    @Transactional
    public Roles createOrGetRole(String tenantId, String roleName, String description, String  adminUserId) {
        log.info("Checking role '{}' for tenantId={}", roleName, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        return rolesRepository.findByNameAndTenant_TenantID(roleName, tenantId)
                .orElseGet(() -> {
                    log.info("Role '{}' not found. Creating new...", roleName);

                    Roles role = new Roles();
                    role.setName(roleName);
                    role.setDescription(description);
                    role.setTenant(tenant);
                    role.setCreatedBy(adminUserId);
                    role.setActive(true);
                    role.setCreatedTime(LocalDateTime.now());
                    role.setIsSuperRole('Y');
                    role.setIsDefault('Y');

                    return rolesRepository.save(role);
                });
    }
}

