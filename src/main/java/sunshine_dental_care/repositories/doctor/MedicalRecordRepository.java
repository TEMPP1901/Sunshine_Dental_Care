package sunshine_dental_care.repositories.doctor;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.MedicalRecord;

import java.util.List;
import java.util.Optional;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Integer> {
    List<MedicalRecord> findByPatientIdOrderByRecordDateDesc(Integer patientId);
    Optional<MedicalRecord> findByIdAndPatientId(Integer id, Integer patientId);
}

