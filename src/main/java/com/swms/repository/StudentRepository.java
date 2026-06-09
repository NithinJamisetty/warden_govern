package com.swms.repository;

import com.swms.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByRollNumber(String rollNumber);
    List<Student> findByHostelName(String hostelName);
    boolean existsByRollNumber(String rollNumber);
    
    @Query("SELECT s FROM Student s WHERE " +
           "(:hostelName IS NULL OR s.hostelName = :hostelName) AND " +
           "(:query IS NULL OR :query = '' OR LOWER(s.studentName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(s.rollNumber) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(s.className) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Student> searchStudents(@Param("hostelName") String hostelName, @Param("query") String query);

    @Query("SELECT s.hostelName, COUNT(s) FROM Student s GROUP BY s.hostelName")
    List<Object[]> getStudentsCountByHostel();

    @Query("SELECT s.className, COUNT(s) FROM Student s GROUP BY s.className")
    List<Object[]> getStudentsCountByClass();
}
