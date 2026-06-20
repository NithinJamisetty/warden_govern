package com.swms.controller;

import com.swms.config.JwtUtils;
import com.swms.config.MfaUtils;
import com.swms.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private MfaUtils mfaUtils;

    @PostMapping("/login")
    public ResponseEntity<?> loginStep1(@RequestBody Map<String, String> request, HttpServletRequest servletRequest) {
        String username = request.get("username");
        String password = request.get("password");
        String ipAddress = getClientIp(servletRequest);

        AuthService.LoginResult res = authService.loginStep1(username, password, ipAddress);
        if (!res.success) {
            Map<String, String> err = new HashMap<>();
            err.put("message", res.message);
            return ResponseEntity.badRequest().body(err);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("message", res.message);
        body.put("role", res.role);
        body.put("tempToken", res.tempToken);
        body.put("mfaRequired", res.mfaRequired);
        body.put("mfaSetupRequired", res.mfaSetupRequired);
        if (res.mfaSetupRequired) {
            body.put("mfaSecret", res.mfaSecret);
            body.put("qrCodeUrl", res.qrCodeUrl);
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<?> loginStep2(@RequestBody Map<String, String> request, HttpServletRequest servletRequest) {
        String tempToken = request.get("tempToken");
        String code = request.get("code");
        String ipAddress = getClientIp(servletRequest);

        AuthService.LoginResult res = authService.loginStep2(tempToken, code, ipAddress);
        if (!res.success) {
            Map<String, String> err = new HashMap<>();
            err.put("message", res.message);
            return ResponseEntity.badRequest().body(err);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("message", res.message);
        body.put("token", res.token);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/register-warden")
    public ResponseEntity<?> registerWardenSelf(@RequestBody Map<String, String> request, HttpServletRequest servletRequest) {
        try {
            String username = request.get("username");
            String mobileNumber = request.get("mobileNumber");
            String password = request.get("password");
            String ipAddress = getClientIp(servletRequest);

            AuthService.LoginResult res = authService.completeWardenRegistration(
                    username,
                    mobileNumber,
                    password,
                    ipAddress
            );

            Map<String, Object> body = new HashMap<>();
            body.put("message", res.message);
            body.put("role", res.role);
            body.put("tempToken", res.tempToken);
            body.put("mfaRequired", res.mfaRequired);
            body.put("mfaSetupRequired", res.mfaSetupRequired);
            body.put("mfaSecret", res.mfaSecret);
            body.put("qrCodeUrl", res.qrCodeUrl);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            Map<String, String> err = new HashMap<>();
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @GetMapping("/mfa/test-code")
    public ResponseEntity<?> getMfaTestCode(@RequestParam("secret") String secret) {
        try {
            String code = mfaUtils.generateCode(secret);
            Map<String, String> body = new HashMap<>();
            body.put("code", code);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body("No token header provided");
        }
        String token = authHeader.substring(7);
        if (jwtUtils.validateToken(token)) {
            Map<String, Object> claims = new HashMap<>();
            claims.put("username", jwtUtils.getUsernameFromToken(token));
            claims.put("role", jwtUtils.getRoleFromToken(token));
            claims.put("hostelName", jwtUtils.getHostelFromToken(token));
            return ResponseEntity.ok(claims);
        }
        return ResponseEntity.status(401).body("Invalid token session");
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    @GetMapping("/public/wardens")
    public ResponseEntity<?> getPublicWardens() {
        return ResponseEntity.ok(authService.getPublicWardensList());
    }
}
