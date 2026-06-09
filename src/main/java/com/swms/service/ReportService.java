package com.swms.service;

import com.swms.repository.StudentRepository;
import com.swms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalStudents = studentRepository.count();
        long totalHostels = studentRepository.getStudentsCountByHostel().size();
        long activeWardens = userRepository.findByRole("WARDEN").stream()
                .filter(u -> u.isActive())
                .count();
                
        stats.put("totalStudents", totalStudents);
        stats.put("totalHostels", totalHostels > 0 ? totalHostels : 0);
        stats.put("activeWardens", activeWardens);
        
        return stats;
    }

    public List<Map<String, Object>> getHostelStats() {
        List<Object[]> raw = studentRepository.getStudentsCountByHostel();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> map = new HashMap<>();
            map.put("hostelName", row[0] != null ? row[0].toString() : "Unknown");
            map.put("studentCount", row[1]);
            list.add(map);
        }
        return list;
    }

    public List<Map<String, Object>> getClassStats() {
        List<Object[]> raw = studentRepository.getStudentsCountByClass();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> map = new HashMap<>();
            map.put("className", row[0] != null ? row[0].toString() : "Unknown");
            map.put("studentCount", row[1]);
            list.add(map);
        }
        return list;
    }
}
