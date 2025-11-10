package sunshine_dental_care.repositories.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.Patient;

import java.util.Optional;


public interface PatientRepo extends JpaRepository<Patient, Integer> {
    Optional<Patient> findByUserId(Integer userId);
}
