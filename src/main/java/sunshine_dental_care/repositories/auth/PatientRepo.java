package sunshine_dental_care.repositories.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.Patient;

import javax.swing.text.html.Option;
import java.util.Optional;

public interface PatientRepo extends JpaRepository<Patient, Integer> {
    Optional<Patient> findByUser_Id (Integer userId);
    Optional<Patient> findPatientCode(String patientCode);
}
