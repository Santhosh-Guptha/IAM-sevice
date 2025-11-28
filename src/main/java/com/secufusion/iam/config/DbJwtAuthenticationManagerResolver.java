package com.secufusion.iam.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.iam.service.AuthConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class DbJwtAuthenticationManagerResolver implements AuthenticationManagerResolver<HttpServletRequest> {

    private final AuthConfigService tenantService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DbJwtAuthenticationManagerResolver(AuthConfigService tenantService) {
        this.tenantService = tenantService;
    }

    @Override
    public AuthenticationManager resolve(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null; // No token, skip
        }

        String token = authHeader.substring(7).trim(); // "Bearer ".length()
        String issuer = extractIssuer(token);
        if (issuer == null) {
            return null;
        }

        Map<String, JwtDecoder> decoders = tenantService.getJwtDecoders(); // Note: JwtDecoder, not Reactive
        JwtDecoder decoder = decoders.get(issuer);
        if (decoder == null) {
            return null;
        }

        // Return manager for this specific tenant's decoder
        return new JwtAuthenticationProvider(decoder)::authenticate;
    }

    private String extractIssuer(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;

            String payload = parts[1];
            // Add padding if needed
            while (payload.length() % 4 != 0) {
                payload += "=";
            }

            String decoded = new String(
                    Base64.getUrlDecoder().decode(payload),
                    StandardCharsets.UTF_8
            );

            JsonNode node = objectMapper.readTree(decoded);
            return node.has("iss") ? node.get("iss").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}