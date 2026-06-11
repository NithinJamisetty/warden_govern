package com.swms.config;

import com.swms.entity.Student;
import com.swms.entity.User;
import com.swms.repository.StudentRepository;
import com.swms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Only seed accounts if they don't already exist to preserve MFA settings across restarts
        if (userRepository.findByUsername("admin").isEmpty()) {
            User superAdmin = new User();
            superAdmin.setUsername("admin");
            superAdmin.setPasswordHash(passwordEncoder.encode("admin123"));
            superAdmin.setMobileNumber("9999999999");
            superAdmin.setRole("SUPER_ADMIN");
            superAdmin.setActive(true);
            superAdmin.setMfaEnabled(false);
            superAdmin.setMfaSecret(null);
            userRepository.save(superAdmin);
        }

        if (userRepository.findByUsername("district_admin").isEmpty()) {
            User districtAdmin = new User();
            districtAdmin.setUsername("district_admin");
            districtAdmin.setPasswordHash(passwordEncoder.encode("district123"));
            districtAdmin.setMobileNumber("8888888888");
            districtAdmin.setRole("DISTRICT_ADMIN");
            districtAdmin.setActive(true);
            districtAdmin.setMfaEnabled(false);
            districtAdmin.setMfaSecret(null);
            userRepository.save(districtAdmin);
        }

        if (userRepository.findByUsername("warden_netaji").isEmpty()) {
            User warden1 = new User();
            warden1.setUsername("warden_netaji");
            warden1.setPasswordHash(passwordEncoder.encode("warden123"));
            warden1.setMobileNumber("7777777777");
            warden1.setHostelName("Netaji Subhash Hostel");
            warden1.setRole("WARDEN");
            warden1.setActive(true);
            warden1.setMfaEnabled(false);
            warden1.setMfaSecret(null);
            userRepository.save(warden1);
        }

        if (userRepository.findByUsername("warden_tagore").isEmpty()) {
            User warden2 = new User();
            warden2.setUsername("warden_tagore");
            warden2.setPasswordHash(passwordEncoder.encode("warden123"));
            warden2.setMobileNumber("6666666666");
            warden2.setHostelName("Rabindranath Tagore Hostel");
            warden2.setRole("WARDEN");
            warden2.setActive(true);
            warden2.setMfaEnabled(false);
            warden2.setMfaSecret(null);
            userRepository.save(warden2);
        }

        System.out.println(">>> SWMS Seed Data: Default accounts seeded.");

        // Seed Students
        if (studentRepository.count() == 0) {
            Student s1 = new Student();
            s1.setStudentName("Aarav Sharma");
            s1.setRollNumber("SWMS2026001");
            s1.setClassName("Class X");
            s1.setParentMobile("9876543210");
            s1.setHostelName("Netaji Subhash Hostel");
            s1.setCreatedBy("warden_netaji");

            Student s2 = new Student();
            s2.setStudentName("Aditya Verma");
            s2.setRollNumber("SWMS2026002");
            s2.setClassName("Class IX");
            s2.setParentMobile("9876543211");
            s2.setHostelName("Netaji Subhash Hostel");
            s2.setCreatedBy("warden_netaji");

            Student s3 = new Student();
            s3.setStudentName("Kabir Das");
            s3.setRollNumber("SWMS2026003");
            s3.setClassName("Class X");
            s3.setParentMobile("9876543212");
            s3.setHostelName("Rabindranath Tagore Hostel");
            s3.setCreatedBy("warden_tagore");

            Student s4 = new Student();
            s4.setStudentName("Vihaan Patel");
            s4.setRollNumber("SWMS2026004");
            s4.setClassName("Class VIII");
            s4.setParentMobile("9876543213");
            s4.setHostelName("Rabindranath Tagore Hostel");
            s4.setCreatedBy("warden_tagore");

            studentRepository.saveAll(Arrays.asList(s1, s2, s3, s4));
            System.out.println(">>> SWMS Seed Data: Default students seeded.");
        }

        // Clean up orphan students or mismatching student records from deleted wardens (like vsGrand / vs grand)
        java.util.List<com.swms.entity.User> activeWardensList = userRepository.findByRole("WARDEN");
        java.util.List<Student> allStudents = studentRepository.findAll();
        for (Student student : allStudents) {
            String studentHostel = student.getHostelName();
            if (studentHostel != null) {
                boolean hasWarden = activeWardensList.stream()
                        .anyMatch(w -> w.getHostelName() != null && 
                                       w.getHostelName().replaceAll("\\s+", "").equalsIgnoreCase(studentHostel.replaceAll("\\s+", "")));
                if (!hasWarden) {
                    studentRepository.delete(student);
                    System.out.println(">>> SWMS Cleanup: Deleted orphan student " + student.getStudentName() + " from unregistered hostel: " + studentHostel);
                }
            }
        }

        // Reset/delete legacy student records for vs grand/vsGrand to reset the bar chart as requested
        java.util.List<Student> legacyStudents = studentRepository.findAll().stream()
                .filter(s -> s.getHostelName() != null && 
                             (s.getHostelName().replaceAll("\\s+", "").equalsIgnoreCase("vsgrand") ||
                              s.getHostelName().replaceAll("\\s+", "").equalsIgnoreCase("vsgrandhostel")))
                .toList();
        for (Student s : legacyStudents) {
            studentRepository.delete(s);
            System.out.println(">>> SWMS Cleanup: Manually deleted legacy student " + s.getStudentName() + " from " + s.getHostelName());
        }

        // Remove legacy warden with username "nithin" or hostel "vs grand" to keep it fully reset
        java.util.List<com.swms.entity.User> legacyWardens = userRepository.findAll().stream()
                .filter(u -> u.getHostelName() != null && 
                             (u.getHostelName().replaceAll("\\s+", "").equalsIgnoreCase("vsgrand") ||
                              u.getUsername().equalsIgnoreCase("nithin")))
                .toList();
        for (com.swms.entity.User w : legacyWardens) {
            userRepository.delete(w);
            System.out.println(">>> SWMS Cleanup: Manually deleted legacy warden " + w.getUsername());
        }
    }
}
