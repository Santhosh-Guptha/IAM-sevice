package com.secufusion.iam.controller;

import com.secufusion.iam.dto.UsersDto;
import com.secufusion.iam.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    // ============================================================
    // AUTH CUSTOM RESPONSE
    // ============================================================
    @GetMapping("/custom-response")
    public ResponseEntity<Map<String, Object>> getCustomResponse(@AuthenticationPrincipal Jwt jwt) {

        Map<String, Object> response = new HashMap<>();
        response.put("userId", jwt.getSubject());
        response.put("username", jwt.getClaimAsString("preferred_username"));
        response.put("email", jwt.getClaimAsString("email"));
        response.put("accessToken", jwt.getTokenValue());
        response.put("message", "Login successful");

        return ResponseEntity.ok(response);
    }


    // ============================================================
    // CREATE USER UNDER TENANT
    // ============================================================
    @PostMapping("/{tenantId}")
    public ResponseEntity<UsersDto> createUser(
            @PathVariable String tenantId,
            @RequestBody UsersDto usersDto) {

        log.info("API: Create user under tenant {}", tenantId);
        UsersDto created = userService.createUser(tenantId, usersDto);
        return ResponseEntity.ok(created);
    }


    // ============================================================
    // GET USER BY ID
    // ============================================================
    @GetMapping("/{userId}")
    public ResponseEntity<UsersDto> getUser(@PathVariable String userId) {

        log.info("API: Fetch user {}", userId);
        UsersDto user = userService.getUser(userId);
        return ResponseEntity.ok(user);
    }


    // ============================================================
    // GET ALL USERS
    // ============================================================
    @GetMapping
    public ResponseEntity<List<UsersDto>> getAllUsers() {

        log.info("API: Fetch all users");
        List<UsersDto> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }


    // ============================================================
    // GET USERS BY TENANT ID
    // ============================================================
    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<UsersDto>> getUsersByTenant(@PathVariable String tenantId) {

        log.info("API: Fetch users for tenant {}", tenantId);
        List<UsersDto> users = userService.getUsersByTenantId(tenantId);
        return ResponseEntity.ok(users);
    }


    // ============================================================
    // UPDATE USER
    // ============================================================
    @PutMapping("/{userId}")
    public ResponseEntity<UsersDto> updateUser(
            @PathVariable String userId,
            @RequestBody UsersDto usersDto) {

        log.info("API: Update user {}", userId);
        UsersDto updated = userService.updateUser(userId, usersDto);
        return ResponseEntity.ok(updated);
    }


    // ============================================================
    // DELETE USER
    // ============================================================
    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable String userId) {

        log.info("API: Delete user {}", userId);

        boolean success = userService.deleteUser(userId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("deleted", success);
        resp.put("userId", userId);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/check")
    public ResponseEntity<String> uniqueValidations(@RequestParam(required = false) String userName,
                                                    @RequestParam(required=false)String phoneNumber,
                                                    @RequestParam (required = false )String email) {
        if(userName != null){
            String result = userService.checkUserName(userName);
            if(result != null){
                return ResponseEntity.ok(result);
            }
        }
        if(phoneNumber != null){
            String result = userService.checkMobileNumber(phoneNumber);
            if(result != null){
                return ResponseEntity.ok(result);
            }
        }
        if(email != null){
            String result = userService.checkEmail(email);
            if(result != null){
                return ResponseEntity.ok(result);
            }
        }
        return ResponseEntity.badRequest().body("");
    }
}
