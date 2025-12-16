package sunshine_dental_care.repositories.doctor;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sunshine_dental_care.entities.MedicalRecord;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Integer> {
    List<MedicalRecord> findByPatientIdOrderByRecordDateDesc(Integer patientId);
    Optional<MedicalRecord> findByIdAndPatientId(Integer id, Integer patientId);
    boolean existsByAppointmentId(Integer appointmentId);

    // Find any medical record associated with an appointment id (used for existence checks)
    Optional<MedicalRecord> findFirstByAppointmentId(Integer appointmentId);

    // Also support records that reference an Appointment via the AppointmentService relation
    Optional<MedicalRecord> findFirstByAppointmentService_Appointment_Id(Integer appointmentId);

    boolean existsByAppointmentService_Appointment_Id(Integer appointmentId);
} 

