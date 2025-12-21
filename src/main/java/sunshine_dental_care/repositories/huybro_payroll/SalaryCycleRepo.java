package sunshine_dental_care.repositories.huybro_payroll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.huybro_salary.SalaryCycle;

import java.util.Optional;

@Repository
public interface SalaryCycleRepo extends JpaRepository<SalaryCycle, Integer> {
    Optional<SalaryCycle> findByMonthAndYear(Integer month, Integer year);
}