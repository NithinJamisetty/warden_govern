package com.swms.controller;

import com.swms.config.UserPrincipal;
import com.swms.entity.Concern;
import com.swms.repository.ConcernRepository;
import com.swms.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ConcernController {

    @Autowired
    private ConcernRepository concernRepository;

    @Autowired
    private AuditLogService auditLogService;

    // Public endpoint for Chatbot ticket submission
    @PostMapping("/api/concerns")
    public ResponseEntity<?> submitConcern(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        String name = payload.get("name");
        String mobileNumber = payload.get("mobileNumber");
        String message = payload.get("message");
        String ipAddress = getClientIp(request);

        if (name == null || name.trim().isEmpty() ||
            mobileNumber == null || mobileNumber.trim().isEmpty() ||
            message == null || message.trim().isEmpty()) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Name, mobile number, and concern description are all required.");
            return ResponseEntity.badRequest().body(response);
        }

        if (message.length() > 1000) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Message cannot exceed 1000 characters.");
            return ResponseEntity.badRequest().body(response);
        }

        Concern concern = new Concern();
        concern.setName(name.trim());
        concern.setMobileNumber(mobileNumber.trim());
        concern.setMessage(message.trim());
        concernRepository.save(concern);

        auditLogService.log("PUBLIC_BOT", "SUBMIT_CONCERN - Name: " + concern.getName() + " | Mobile: " + concern.getMobileNumber(), ipAddress);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Concern submitted successfully.");
        return ResponseEntity.ok(response);
    }

    // Admin endpoint to retrieve all concerns
    @GetMapping("/api/admin/concerns")
    public ResponseEntity<List<Concern>> getConcerns() {
        List<Concern> concerns = concernRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(concerns);
    }

    // Admin endpoint to delete/dismiss concern
    @DeleteMapping("/api/admin/concerns/{id}")
    public ResponseEntity<?> dismissConcern(@PathVariable("id") Long id, HttpServletRequest request) {
        String ipAddress = getClientIp(request);
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!concernRepository.existsById(id)) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Concern ticket not found.");
            return ResponseEntity.notFound().build();
        }

        concernRepository.deleteById(id);
        auditLogService.log(principal.getName(), "DISMISS_CONCERN - ID: " + id, ipAddress);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Concern dismissed successfully.");
        return ResponseEntity.ok(response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
