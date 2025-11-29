package com.secufusion.iam.util;

import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.repository.TenantRepository;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.ParseException;

@Service
public class JwtUtl {


    @Autowired
    private TenantRepository tenantRepository;

    // Extract full token from Authorization header
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    // Decode JWT safely
    private JWTClaimsSet decodeToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            return jwt.getJWTClaimsSet();
        } catch (Exception e) {
            throw new RuntimeException("Invalid JWT Token: " + e.getMessage());
        }
    }

    // Get username (preferred_username)
public String getUsername(HttpServletRequest request) {
    String token = extractToken(request);
    if (token == null) return null;

    try {
        JWTClaimsSet claims = decodeToken(token);
        return claims.getStringClaim("preferred_username");
    } catch (Exception e) {
        return null;
    }
}

    // Get email
    public String getEmail(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) return null;

        try {
            JWTClaimsSet claims = decodeToken(token);
            return claims.getStringClaim("email");
        } catch (Exception e) {
            return null;
        }
    }

    public Tenant getTenantFromEmail(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) return null;

        try {
            JWTClaimsSet claims = decodeToken(token);
            String email = claims.getStringClaim("email");
            if (email == null) return null;

            if (tenantRepository == null) return null;

            try {
                return tenantRepository.findByEmail(email).orElse(null);
            } catch (Exception e) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    // Optional: Get userId (sub)
    public String getUserId(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) return null;

        JWTClaimsSet claims = decodeToken(token);
        return claims.getSubject(); // "sub"
    }
}
