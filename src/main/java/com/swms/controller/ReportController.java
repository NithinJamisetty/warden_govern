package com.swms.controller;

import com.swms.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/hostels")
    public ResponseEntity<List<Map<String, Object>>> getHostelStats() {
        return ResponseEntity.ok(reportService.getHostelStats());
    }

    @GetMapping("/classes")
    public ResponseEntity<List<Map<String, Object>>> getClassStats() {
        return ResponseEntity.ok(reportService.getClassStats());
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        return ResponseEntity.ok(reportService.getDashboardStats());
    }
}
