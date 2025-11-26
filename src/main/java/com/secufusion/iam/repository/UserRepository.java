package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Serializable> {
    Optional<User> findByUserName(String userName);
    Optional<User> findByEmail(String email);

    List<User> findByTenant(Tenant t);

    Optional<User> findByUserNameAndTenant_TenantID(String userName, String tenantId);

    Optional<User> findByPhoneNoAndTenant_TenantID(String phoneNumber, String tenantId);

    Optional<User> findByEmailAndTenant_TenantID(String email, String tenantId);
}
