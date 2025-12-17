package sunshine_dental_care.repositories.huybro_payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.huybro_salary.SalaryAllowance;

@Repository
public interface SalaryAllowanceRepo extends JpaRepository<SalaryAllowance, Integer> {
    void deleteAllBySalaryProfileId(Integer salaryProfileId);
}
