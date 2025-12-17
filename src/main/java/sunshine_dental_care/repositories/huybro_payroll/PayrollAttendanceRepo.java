package sunshine_dental_care.repositories.huybro_payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.Attendance;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PayrollAttendanceRepo extends JpaRepository<Attendance, Integer> {

    // Hàm này dùng để lấy tất cả record chấm công của 1 user trong khoảng thời gian (Start -> End)
    List<Attendance> findByUserIdAndWorkDateBetween(Integer userId, LocalDate startDate, LocalDate endDate);
}