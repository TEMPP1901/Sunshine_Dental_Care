package sunshine_dental_care.repositories.huybro_payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.huybro_salary.PayslipAllowance;

@Repository
public interface PayslipAllowanceRepo extends JpaRepository<PayslipAllowance, Integer> {
    // Có thể thêm method tìm kiếm nếu cần sau này
}