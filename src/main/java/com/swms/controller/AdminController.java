package com.swms.controller;

import com.swms.config.UserPrincipal;
import com.swms.entity.User;
import com.swms.service.AuthService;
import com.swms.service.AuditLogService;
import com.swms.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AuthService authService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private ReportService reportService;

    @GetMapping("/wardens")
    public ResponseEntity<List<User>> getWardens() {
        return ResponseEntity.ok(authService.getWardens());
    }

    @PostMapping("/register-warden")
    public ResponseEntity<?> registerWarden(@RequestBody Map<String, String> request, HttpServletRequest servletRequest) {
        try {
            UserPrincipal principal = getPrincipal();
            String username = request.get("username");
            String password = request.get("password");
            String mobileNumber = request.get("mobileNumber");
            String hostelName = request.get("hostelName");
            String ipAddress = getClientIp(servletRequest);

            User warden = authService.registerWarden(
                    username,
                    password,
                    mobileNumber,
                    hostelName,
                    principal.getName(),
                    ipAddress
            );
            return ResponseEntity.ok(warden);
        } catch (IllegalArgumentException e) {
            Map<String, String> err = new HashMap<>();
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @PostMapping("/wardens/{id}/reset")
    public ResponseEntity<?> resetWarden(@PathVariable("id") Long id, HttpServletRequest servletRequest) {
        UserPrincipal principal = getPrincipal();
        String ipAddress = getClientIp(servletRequest);
        authService.resetWardenAccount(id, principal.getName(), ipAddress);
        Map<String, String> msg = new HashMap<>();
        msg.put("message", "Warden account and MFA configurations have been reset successfully.");
        return ResponseEntity.ok(msg);
    }

    @PostMapping("/wardens/{id}/toggle")
    public ResponseEntity<?> toggleWarden(@PathVariable("id") Long id, HttpServletRequest servletRequest) {
        UserPrincipal principal = getPrincipal();
        String ipAddress = getClientIp(servletRequest);
        authService.toggleWardenStatus(id, principal.getName(), ipAddress);
        Map<String, String> msg = new HashMap<>();
        msg.put("message", "Warden active state toggled successfully.");
        return ResponseEntity.ok(msg);
    }

    @GetMapping("/logs")
    public ResponseEntity<?> getLogs(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(auditLogService.getLogs(page, size));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(reportService.getDashboardStats());
    }

    private UserPrincipal getPrincipal() {
        return (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
