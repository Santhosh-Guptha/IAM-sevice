package com.secufusion.iam.controller;

import com.secufusion.iam.dto.AuthDetailsDto;
import com.secufusion.iam.service.AuthConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping
public class AuthController {

    @Autowired
    private AuthConfigService authConfigService;

    @GetMapping("/tenant-config")
    public ResponseEntity<AuthDetailsDto> getTenantConfig(@RequestParam String host) {
        return ResponseEntity.ok(authConfigService.getTenantConfig(host));
    }
}
