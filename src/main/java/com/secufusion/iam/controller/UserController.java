package com.secufusion.iam.controller;

import com.secufusion.iam.service.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private TenantService tenantService;

    @GetMapping("custom-response")
    public ResponseEntity<Map<String, Object>> getCustomResponse(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("userId", jwt.getSubject());
        response.put("username", jwt.getClaimAsString("preferred_username"));
        response.put("email", jwt.getClaimAsString("email"));
        response.put("accessToken", jwt.getTokenValue());
        response.put("message", "Login successful");
        return ResponseEntity.ok(response);
    }


    @GetMapping("/auth/token")
    public ResponseEntity<?> getAccessToken(@RequestParam String code){

        Map<String, Object> tokenResponse = tenantService.getAccessToken(code);
        return ResponseEntity.ok(tokenResponse);

    }

}


