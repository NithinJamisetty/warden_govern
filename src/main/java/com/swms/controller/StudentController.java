package com.swms.controller;

import com.swms.config.UserPrincipal;
import com.swms.entity.Student;
import com.swms.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    @Autowired
    private StudentService studentService;

    @GetMapping
    public ResponseEntity<List<Student>> getStudents(@RequestParam(value = "query", required = false) String query) {
        UserPrincipal principal = getPrincipal();
        List<Student> students = studentService.searchStudents(
                principal.getName(),
                principal.getRole(),
                principal.getHostelName(),
                query
        );
        return ResponseEntity.ok(students);
    }

    @PostMapping
    public ResponseEntity<?> addStudent(@RequestBody Student student, HttpServletRequest request) {
        try {
            UserPrincipal principal = getPrincipal();
            String ipAddress = getClientIp(request);
            Student saved = studentService.addStudent(
                    student,
                    principal.getName(),
                    principal.getRole(),
                    principal.getHostelName(),
                    ipAddress
            );
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            Map<String, String> err = new HashMap<>();
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateStudent(@PathVariable("id") Long id, @RequestBody Student student, HttpServletRequest request) {
        try {
            UserPrincipal principal = getPrincipal();
            String ipAddress = getClientIp(request);
            Student updated = studentService.updateStudent(
                    id,
                    student,
                    principal.getName(),
                    principal.getRole(),
                    principal.getHostelName(),
                    ipAddress
            );
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            Map<String, String> err = new HashMap<>();
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStudent(@PathVariable("id") Long id, HttpServletRequest request) {
        try {
            UserPrincipal principal = getPrincipal();
            String ipAddress = getClientIp(request);
            studentService.deleteStudent(
                    id,
                    principal.getName(),
                    principal.getRole(),
                    principal.getHostelName(),
                    ipAddress
            );
            Map<String, String> msg = new HashMap<>();
            msg.put("message", "Student record deleted successfully");
            return ResponseEntity.ok(msg);
        } catch (IllegalArgumentException e) {
            Map<String, String> err = new HashMap<>();
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
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
