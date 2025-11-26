package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "TenantTypes")
@Data
public class TenantType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tenantTypeID;

    private String tenantTypeName;

}
