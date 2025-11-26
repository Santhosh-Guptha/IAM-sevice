package com.secufusion.iam.controller;

import com.secufusion.iam.dto.CreateTenantRequest;
import com.secufusion.iam.dto.TenantResponse;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.TenantType;
import com.secufusion.iam.service.TenantService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/tenants")
@Slf4j
public class TenantController {

    @Autowired
    private TenantService tenantService;


    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest req) {
        log.info("Request to create tenant realm={}", req.getTenantName());
        TenantResponse resp = tenantService.createTenant(req);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/id")
    public ResponseEntity<TenantResponse> getTenant(@RequestParam String id) {
        return ResponseEntity.ok(tenantService.getTenant(id));
    }

    @GetMapping
    public ResponseEntity<List<Tenant>> getAll(){
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    @PutMapping("/{id}")
    public ResponseEntity<TenantResponse> updateTenant(@PathVariable String id, @Valid @RequestBody CreateTenantRequest req) {
        return ResponseEntity.ok(tenantService.updateTenant(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTenant(@PathVariable String id) {
        tenantService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/types")
    public ResponseEntity<List<TenantType>> getAllTenantTypes(){
        return ResponseEntity.ok(tenantService.getAllTenantTypes());
    }

    @GetMapping("/billing")
    public ResponseEntity<List<Map<String, Object>>> getBillingTypes(){
        return ResponseEntity.ok(tenantService.getTenantBillingTypes());
    }
}
