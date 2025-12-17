package sunshine_dental_care.repositories.huybro_payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.huybro_salary.SalaryProfile;

import java.util.Optional;

@Repository
public interface SalaryProfileRepo extends JpaRepository<SalaryProfile, Integer> {
    Optional<SalaryProfile> findByUserId(Integer userId);
}