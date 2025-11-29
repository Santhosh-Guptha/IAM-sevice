package com.secufusion.iam.controller;

import com.secufusion.iam.dto.CreateTenantRequest;
import com.secufusion.iam.dto.TenantResponse;
import com.secufusion.iam.entity.TenantType;
import com.secufusion.iam.service.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
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
    public ResponseEntity<TenantResponse> createTenant(HttpServletRequest request, @Valid @RequestBody CreateTenantRequest req) {
        log.info("Request to create tenant realm={}", req.getTenantName());
        TenantResponse resp = tenantService.createTenant(request,req);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/id")
    public ResponseEntity<TenantResponse> getTenant(HttpServletRequest request, @RequestParam String id) {
        return ResponseEntity.ok(tenantService.getTenantIfParent(request, id));
    }

    @GetMapping
    public ResponseEntity<List<TenantResponse>> getAll(HttpServletRequest request){
        return ResponseEntity.ok(tenantService.getTenantHierarchy(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TenantResponse> updateTenant(HttpServletRequest request, @PathVariable String id, @Valid @RequestBody CreateTenantRequest req) {
        return ResponseEntity.ok(tenantService.updateTenant(request, id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTenant(@PathVariable String id) {
        tenantService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/types")
    public ResponseEntity<List<TenantType>> getAllTenantTypes(HttpServletRequest request){
        return ResponseEntity.ok(tenantService.getTenantTypesByTenantType(request));
    }

    @GetMapping("/billing")
    public ResponseEntity<List<Map<String, Object>>> getBillingTypes(){
        return ResponseEntity.ok(tenantService.getTenantBillingTypes());
    }

    @GetMapping("/check")
    public ResponseEntity<String> checkTenant(
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) String domainName,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String tenantEmail) {

        if (tenantName != null) {
            String result = tenantService.checkTenantNameAvailability(tenantName);
            return ResponseEntity.ok(result);
        }

        if (domainName != null) {
            String result = tenantService.checkExistsByDomain(domainName);
            return ResponseEntity.ok(result);
        }

        if (phoneNumber != null) {
            String result = tenantService.checkPhoneNumber(phoneNumber);
            return ResponseEntity.ok(result);
        }

        if (tenantEmail != null) {
            String result = tenantService.checkEmail(tenantEmail);
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.badRequest().body("");
    }

}
