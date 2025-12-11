package sunshine_dental_care.repositories.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sunshine_dental_care.entities.Patient;

import java.util.Optional;


public interface PatientRepo extends JpaRepository<Patient, Integer> {
    Optional<Patient> findByUserId(Integer userId);

    // Tìm kiếm bệnh nhân (Dùng cho cả List và Search Box)
    // Tìm theo: Tên OR SĐT OR Mã BN OR Email
    @Query("SELECT p FROM Patient p WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "p.phone LIKE CONCAT('%', :keyword, '%') OR " +
            "LOWER(p.patientCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Patient> searchPatients(@Param("keyword") String keyword, Pageable pageable);

    // Tìm nhanh không phân trang (Dùng cho dropdown select nếu cần)
    @Query("SELECT p FROM Patient p WHERE " +
            "LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "p.phone LIKE CONCAT('%', :keyword, '%')")
    java.util.List<Patient> findTop10ByKeyword(@Param("keyword") String keyword);
}
