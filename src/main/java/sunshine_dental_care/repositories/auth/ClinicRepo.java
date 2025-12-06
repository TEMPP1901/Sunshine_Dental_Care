package sunshine_dental_care.repositories.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.Clinic;
import java.util.List;

public interface ClinicRepo extends JpaRepository<Clinic, Integer> {
    List<Clinic> findByIsActiveTrue();
}