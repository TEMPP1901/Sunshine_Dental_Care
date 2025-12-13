package sunshine_dental_care.repositories.doctor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sunshine_dental_care.entities.MedicalRecord;

import java.util.List;
import java.util.Optional;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Integer> {
    List<MedicalRecord> findByPatientIdOrderByRecordDateDesc(Integer patientId);
    Optional<MedicalRecord> findByIdAndPatientId(Integer id, Integer patientId);


    @Query("select m from MedicalRecord m " +
            "left join fetch m.service s " +
            "left join fetch s.variants v " +
            "where m.id = :recordId and m.patient.id = :patientId")
    Optional<MedicalRecord> findByIdAndPatientIdWithServiceAndVariants(@Param("recordId") Integer recordId,
                                                                       @Param("patientId") Integer patientId);


}

