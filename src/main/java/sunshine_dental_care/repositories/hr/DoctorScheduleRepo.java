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
        @Query("SELECT d FROM DoctorSchedule d WHERE d.workDate = :workDate AND d.doctor.isActive = true ORDER BY d.doctor.id")
        List<DoctorSchedule> findByWorkDate(@Param("workDate") LocalDate workDate);

        // Lấy lịch cho cả tuần (từ Monday đến Saturday, 6 ngày)
        @Query("SELECT d FROM DoctorSchedule d WHERE d.workDate >= :weekStart AND d.workDate <= :weekEnd AND d.doctor.isActive = true ORDER BY d.workDate, d.doctor.id")
        List<DoctorSchedule> findByWeekRange(@Param("weekStart") LocalDate weekStart,
                        @Param("weekEnd") LocalDate weekEnd);

        // Lấy lịch của cơ sở theo ngày
        @Query("SELECT d FROM DoctorSchedule d WHERE d.clinic.id = :clinicId AND d.workDate = :workDate AND d.doctor.isActive = true ORDER BY d.doctor.id")
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

        // Lấy lịch theo khoảng thời gian và clinic (dùng để tìm schedules trùng với holiday)
        // Nếu clinicId = null, lấy tất cả schedules trong khoảng thời gian
        // Chỉ lấy ACTIVE hoặc NULL status (dùng khi tạo holiday)
        @Query("SELECT d FROM DoctorSchedule d WHERE d.workDate >= :startDate AND d.workDate <= :endDate " +
               "AND (:clinicId IS NULL OR d.clinic.id = :clinicId) " +
               "AND d.doctor.isActive = true " +
               "AND (d.status IS NULL OR d.status = 'ACTIVE') " +
               "ORDER BY d.workDate, d.clinic.id, d.doctor.id")
        List<DoctorSchedule> findByDateRangeAndClinic(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("clinicId") Integer clinicId);

        // Lấy lịch theo khoảng thời gian và clinic bao gồm cả CANCELLED (dùng để restore khi xóa holiday)
        // Nếu clinicId = null, lấy tất cả schedules trong khoảng thời gian
        @Query("SELECT d FROM DoctorSchedule d WHERE d.workDate >= :startDate AND d.workDate <= :endDate " +
               "AND (:clinicId IS NULL OR d.clinic.id = :clinicId) " +
               "AND d.doctor.isActive = true " +
               "ORDER BY d.workDate, d.clinic.id, d.doctor.id")
        List<DoctorSchedule> findByDateRangeAndClinicAllStatus(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("clinicId") Integer clinicId);
}