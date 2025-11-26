package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Entity
@Table(name = "groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Groups {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String pkGroupId;

    private Character isAdmin = 'N';
    private Character isDefault = 'N';

    private String description;

    /** Each group belongs to one tenant */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /** Group ↔ Users = M:N */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_group_map",
            joinColumns = @JoinColumn(name = "fk_group_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_user_id")
    )
    private Set<User> mappedUsers;

    /** Group ↔ Roles = M:N */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "group_role_map",
            joinColumns = @JoinColumn(name = "fk_group_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_role_id")
    )
    private Set<Roles> mappedRoles;
}
