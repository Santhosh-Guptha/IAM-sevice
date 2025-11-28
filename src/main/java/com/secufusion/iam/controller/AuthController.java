package com.secufusion.iam.controller;

import com.secufusion.iam.dto.AuthDetailsDto;
import com.secufusion.iam.service.AuthConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping
public class AuthController {

    @Autowired
    private AuthConfigService authConfigService;

//    @GetMapping("/tenant-config")
//    public ResponseEntity<AuthDetailsDto> getTenantConfig(
//            HttpServletRequest request,
//            @RequestParam String host
//    ) {
//
//        // Extract referer
//        String refererHeader = request.getHeader("Referer");
//        if (refererHeader == null || refererHeader.isBlank()) {
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
//        }
//
//        try {
//            // Domain from referer
//            URI refererUri = new URI(refererHeader);
//            String refererDomain = refererUri.getHost();
//
//            // Domain from request URL
//            URI requestUri = new URI(request.getRequestURL().toString());
//            String requestDomain = requestUri.getHost();
//
//            // Normalize host param
//            String expectedDomain = host.toLowerCase().trim();
//
//            log.info("Validation check => requestDomain={}, refererDomain={}, hostParam={}",
//                    requestDomain, refererDomain, expectedDomain);
//
//            // STRICT MATCHING RULES
//            if (!expectedDomain.equalsIgnoreCase(refererDomain) ||
//                    !expectedDomain.equalsIgnoreCase(requestDomain) ||
//                    !refererDomain.equalsIgnoreCase(requestDomain)) {
//
//                log.warn("Domain validation failed");
//                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
//            }
//
//            // Passed all checks → return config
//            return ResponseEntity.ok(authConfigService.getTenantConfig(host));
//
//        } catch (Exception e) {
//            log.error("Error validating referer", e);
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
//        }
//    }

    @GetMapping("/tenant-config")
    public ResponseEntity<AuthDetailsDto> getTenantConfig(@RequestParam String host) {
        return ResponseEntity.ok(authConfigService.getTenantConfig(host));
    }


}
