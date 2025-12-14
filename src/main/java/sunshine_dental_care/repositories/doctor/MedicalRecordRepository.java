package sunshine_dental_care.repositories.doctor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.MedicalRecord;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Integer> {

    // --- CÁC HÀM CŨ (Giữ lại để không lỗi code cũ) ---
    List<MedicalRecord> findByPatientIdOrderByRecordDateDesc(Integer patientId);
    Optional<MedicalRecord> findByIdAndPatientId(Integer id, Integer patientId);

    // --- HÀM MỚI (Thêm vào để dùng cho Dashboard) ---
    // Lấy hồ sơ bệnh án mới nhất (dựa trên thời gian tạo)
    List<MedicalRecord> findByPatientIdOrderByCreatedAtDesc(Integer patientId);
}