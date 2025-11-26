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
}
