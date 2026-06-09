package com.swms.service;

import com.swms.entity.Student;
import com.swms.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StudentService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AuditLogService auditLogService;

    public List<Student> searchStudents(String username, String role, String wardenHostel, String query) {
        if ("SUPER_ADMIN".equals(role) || "DISTRICT_ADMIN".equals(role)) {
            return studentRepository.searchStudents(null, query);
        } else {
            // Warden is locked to their hostel
            return studentRepository.searchStudents(wardenHostel, query);
        }
    }

    public Student addStudent(Student student, String username, String role, String wardenHostel, String ipAddress) {
        if (!"WARDEN".equals(role)) {
            throw new AccessDeniedException("Only authorized Wardens can add students.");
        }
        
        // Ensure student is saved in warden's assigned hostel
        student.setHostelName(wardenHostel);
        student.setCreatedBy(username);
        
        if (studentRepository.existsByRollNumber(student.getRollNumber())) {
            throw new IllegalArgumentException("Student with roll number " + student.getRollNumber() + " already exists.");
        }

        Student saved = studentRepository.save(student);
        auditLogService.log(username, "ADD_STUDENT - Roll: " + saved.getRollNumber() + ", Name: " + saved.getStudentName(), ipAddress);
        return saved;
    }

    public Student updateStudent(Long id, Student details, String username, String role, String wardenHostel, String ipAddress) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        if ("WARDEN".equals(role) && !student.getHostelName().equalsIgnoreCase(wardenHostel)) {
            throw new AccessDeniedException("Access denied. You can only update student records for your assigned hostel.");
        }

        // Check roll number uniqueness if changed
        if (!student.getRollNumber().equalsIgnoreCase(details.getRollNumber()) && 
            studentRepository.existsByRollNumber(details.getRollNumber())) {
            throw new IllegalArgumentException("Student with roll number " + details.getRollNumber() + " already exists.");
        }

        student.setStudentName(details.getStudentName());
        student.setRollNumber(details.getRollNumber());
        student.setClassName(details.getClassName());
        student.setParentMobile(details.getParentMobile());

        // Only allow non-wardens to change hostel of student, wardens remain locked to their hostel
        if (!"WARDEN".equals(role) && details.getHostelName() != null) {
            student.setHostelName(details.getHostelName());
        }

        Student updated = studentRepository.save(student);
        auditLogService.log(username, "UPDATE_STUDENT - Roll: " + updated.getRollNumber() + ", Name: " + updated.getStudentName(), ipAddress);
        return updated;
    }

    public void deleteStudent(Long id, String username, String role, String wardenHostel, String ipAddress) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        if ("WARDEN".equals(role) && !student.getHostelName().equalsIgnoreCase(wardenHostel)) {
            throw new AccessDeniedException("Access denied. You can only delete student records for your assigned hostel.");
        }

        studentRepository.delete(student);
        auditLogService.log(username, "DELETE_STUDENT - Roll: " + student.getRollNumber() + ", Name: " + student.getStudentName(), ipAddress);
    }
}
