package sunshine_dental_care.repositories.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sunshine_dental_care.entities.Clinic;

public interface ClinicRepo extends JpaRepository<Clinic, Integer> {
    Optional<Clinic> findByClinicCode(String clinicCode);
}
