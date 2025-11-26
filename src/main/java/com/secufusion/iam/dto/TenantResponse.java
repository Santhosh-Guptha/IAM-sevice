package com.secufusion.iam.dto;

import com.secufusion.iam.entity.Address;
import lombok.Data;

import java.time.Instant;

@Data
public class TenantResponse {
    private String tenantID;
    private String tenantName;
    private String realmName;
    private String domain;
    private String region;
    private String phoneNo;
    private String tenantType;
    private String industry;
    private Address temporaryAddress;
    private Address permanentAddress;
    private Address billingAddress;
    private String status;
    private Instant createdAt;
    private String loginUrl;

}
