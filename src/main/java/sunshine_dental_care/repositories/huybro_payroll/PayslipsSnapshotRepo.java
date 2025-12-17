package sunshine_dental_care.repositories.huybro_payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.huybro_salary.PayslipsSnapshot;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayslipsSnapshotRepo extends JpaRepository<PayslipsSnapshot, Integer>, JpaSpecificationExecutor<PayslipsSnapshot> {
    // Tìm phiếu lương của user trong 1 kỳ lương cụ thể
    Optional<PayslipsSnapshot> findBySalaryCycleIdAndUserId(Integer salaryCycleId, Integer userId);

    // Lấy danh sách phiếu lương của cả kỳ (để hiển thị bảng tổng hợp)
    List<PayslipsSnapshot> findBySalaryCycleId(Integer salaryCycleId);
}