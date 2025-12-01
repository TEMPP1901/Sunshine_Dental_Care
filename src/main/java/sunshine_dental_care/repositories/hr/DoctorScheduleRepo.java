package sunshine_dental_care.repositories.hr;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.DoctorSchedule;

@Repository
public interface DoctorScheduleRepo extends JpaRepository<DoctorSchedule, Integer> {

    // Lấy lịch theo ngày cụ thể
    @Query("SELECT d FROM DoctorSchedule d WHERE d.workDate = :workDate ORDER BY d.doctor.id")
    List<DoctorSchedule> findByWorkDate(@Param("workDate") LocalDate workDate);

    // Lấy lịch cho cả tuần (từ Monday đến Saturday, 6 ngày)
    @Query("SELECT d FROM DoctorSchedule d WHERE d.workDate >= :weekStart AND d.workDate <= :weekEnd ORDER BY d.workDate, d.doctor.id")
    List<DoctorSchedule> findByWeekRange(@Param("weekStart") LocalDate weekStart, @Param("weekEnd") LocalDate weekEnd);

    // Lấy lịch của cơ sở theo ngày
    @Query("SELECT d FROM DoctorSchedule d WHERE d.clinic.id = :clinicId AND d.workDate = :workDate ORDER BY d.doctor.id")
    List<DoctorSchedule> findByClinicAndDate(@Param("clinicId") Integer clinicId,
            @Param("workDate") LocalDate workDate);

    // Kiểm tra bác sĩ đã được phân công chưa trong ngày
    @Query("SELECT COUNT(d) > 0 FROM DoctorSchedule d WHERE d.doctor.id = :doctorId AND d.workDate = :workDate")
    boolean existsByDoctorAndDate(@Param("doctorId") Integer doctorId, @Param("workDate") LocalDate workDate);

    // Lấy schedule của user tại clinic trong ngày (chỉ ACTIVE)
    // Lấy lịch làm việc tại CLINIC KHÁCH CHỌN thêm điều kiện status = 'ACTIVE' của
    // bác sĩ tại clinicId đó
    @Query("SELECT d FROM DoctorSchedule d WHERE d.doctor.id = :userId AND d.clinic.id = :clinicId AND d.workDate = :workDate AND d.status = 'ACTIVE'")
    List<DoctorSchedule> findByUserIdAndClinicIdAndWorkDate(
            @Param("userId") Integer userId,
            @Param("clinicId") Integer clinicId,
            @Param("workDate") LocalDate workDate);

    // Lấy schedule của user tại clinic trong ngày (tất cả status - dùng để update)
    @Query("SELECT d FROM DoctorSchedule d WHERE d.doctor.id = :userId AND d.clinic.id = :clinicId AND d.workDate = :workDate ORDER BY d.startTime")
    List<DoctorSchedule> findByUserIdAndClinicIdAndWorkDateAllStatus(
            @Param("userId") Integer userId,
            @Param("clinicId") Integer clinicId,
            @Param("workDate") LocalDate workDate);

    // Lấy tất cả schedule của bác sĩ trong ngày
    @Query("SELECT d FROM DoctorSchedule d WHERE d.doctor.id = :doctorId AND d.workDate = :workDate ORDER BY d.startTime")
    List<DoctorSchedule> findByDoctorIdAndWorkDate(
            @Param("doctorId") Integer doctorId,
            @Param("workDate") LocalDate workDate);

    // Lấy lịch của bác sĩ trong khoảng thời gian
    @Query("SELECT d FROM DoctorSchedule d WHERE d.doctor.id = :doctorId AND d.workDate >= :startDate AND d.workDate <= :endDate ORDER BY d.workDate, d.startTime")
    List<DoctorSchedule> findByDoctorIdAndDateRange(
            @Param("doctorId") Integer doctorId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}