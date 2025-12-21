package sunshine_dental_care.repositories.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sunshine_dental_care.entities.Clinic;

public interface ClinicRepo extends JpaRepository<Clinic, Integer> {

    // --- 1. PHẦN CỦA TUẤN (Lấy danh sách Active) ---
    List<Clinic> findByIsActiveTrue();
    
    // Đếm số phòng khám đang hoạt động
    long countByIsActiveTrue();

    // --- 2. PHẦN CỦA LONG (Tìm theo mã code) ---
    Optional<Clinic> findByClinicCode(String clinicCode);
}