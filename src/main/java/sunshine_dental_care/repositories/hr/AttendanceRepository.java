package sunshine_dental_care.repositories.hr;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.Attendance;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Integer> {

    // Tìm bản ghi chấm công của user trong ngày
    Optional<Attendance> findByUserIdAndWorkDate(Integer userId, LocalDate workDate);

    // Tìm bản ghi chấm công của user tại phòng khám trong ngày
    Optional<Attendance> findByUserIdAndClinicIdAndWorkDate(Integer userId, Integer clinicId, LocalDate workDate);

    // Lấy danh sách chấm công của user tại phòng khám trong ngày (cho bác sĩ có nhiều ca)
    List<Attendance> findAllByUserIdAndClinicIdAndWorkDate(Integer userId, Integer clinicId, LocalDate workDate);

    // Tìm bản ghi chấm công của user theo ca làm việc
    Optional<Attendance> findByUserIdAndClinicIdAndWorkDateAndShiftType(
        Integer userId, Integer clinicId, LocalDate workDate, String shiftType);

    // Lấy danh sách chấm công của user trong ngày (tất cả các ca)
    List<Attendance> findAllByUserIdAndWorkDate(Integer userId, LocalDate workDate);

    // Lấy danh sách lịch sử chấm công của user trong khoảng thời gian, sắp xếp mới nhất trước
    List<Attendance> findByUserIdAndWorkDateBetweenOrderByWorkDateDesc(
        Integer userId, LocalDate startDate, LocalDate endDate);

    // Lấy danh sách lịch sử chấm công của user trong khoảng thời gian, có phân trang
    Page<Attendance> findByUserIdAndWorkDateBetweenOrderByWorkDateDesc(
        Integer userId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    // Lấy lịch sử chấm công của user tại một clinic trong khoảng thời gian
    List<Attendance> findByUserIdAndClinicIdAndWorkDateBetweenOrderByWorkDateDesc(
        Integer userId, Integer clinicId, LocalDate startDate, LocalDate endDate);

    // Lấy lịch sử chấm công của user tại một clinic trong khoảng thời gian, có phân trang
    Page<Attendance> findByUserIdAndClinicIdAndWorkDateBetweenOrderByWorkDateDesc(
        Integer userId, Integer clinicId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Attendance> findByClinicIdAndWorkDate(Integer clinicId, LocalDate workDate);

    Page<Attendance> findByClinicIdAndWorkDateBetween(
        Integer clinicId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Attendance> findByWorkDate(LocalDate workDate);

    List<Attendance> findByWorkDateBetween(LocalDate startDate, LocalDate endDate);

    List<Attendance> findByWorkDateAndAttendanceStatus(LocalDate workDate, String attendanceStatus);

    List<Attendance> findByClinicIdAndWorkDateAndAttendanceStatus(Integer clinicId, LocalDate workDate, String attendanceStatus);

    // Đếm số lần chấm công của user trong khoảng thời gian
    long countByUserIdAndWorkDateBetween(Integer userId, LocalDate startDate, LocalDate endDate);

    // Đếm số lần chấm công của user tại một clinic trong khoảng thời gian
    long countByUserIdAndClinicIdAndWorkDateBetween(
        Integer userId, Integer clinicId, LocalDate startDate, LocalDate endDate);

    // Kiểm tra user đã check-in trong ngày hay chưa
    @Query("SELECT COUNT(a) > 0 FROM Attendance a WHERE a.userId = :userId AND a.workDate = :workDate AND a.checkInTime IS NOT NULL")
    boolean existsByUserIdAndWorkDateWithCheckIn(@Param("userId") Integer userId, @Param("workDate") LocalDate workDate);

    // Kiểm tra user đã check-out trong ngày hay chưa
    @Query("SELECT COUNT(a) > 0 FROM Attendance a WHERE a.userId = :userId AND a.workDate = :workDate AND a.checkOutTime IS NOT NULL")
    boolean existsByUserIdAndWorkDateWithCheckOut(@Param("userId") Integer userId, @Param("workDate") LocalDate workDate);

    // ========== QUERY METHODS CHO KẾ TOÁN TÍNH LƯƠNG ==========
    
    // Tính tổng giờ làm việc (actualWorkHours) của user trong khoảng thời gian
    // Dùng để tính lương theo giờ
    @Query("SELECT COALESCE(SUM(a.actualWorkHours), 0) FROM Attendance a " +
           "WHERE a.userId = :userId AND a.workDate BETWEEN :startDate AND :endDate " +
           "AND a.actualWorkHours IS NOT NULL")
    java.math.BigDecimal sumActualWorkHoursByUserIdAndWorkDateBetween(
            @Param("userId") Integer userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Tính tổng giờ làm việc của user tại một clinic trong khoảng thời gian
    @Query("SELECT COALESCE(SUM(a.actualWorkHours), 0) FROM Attendance a " +
           "WHERE a.userId = :userId AND a.clinicId = :clinicId " +
           "AND a.workDate BETWEEN :startDate AND :endDate " +
           "AND a.actualWorkHours IS NOT NULL")
    java.math.BigDecimal sumActualWorkHoursByUserIdAndClinicIdAndWorkDateBetween(
            @Param("userId") Integer userId,
            @Param("clinicId") Integer clinicId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Lấy danh sách attendance có actualWorkHours trong khoảng thời gian (cho kế toán)
    @Query("SELECT a FROM Attendance a WHERE a.userId = :userId " +
           "AND a.workDate BETWEEN :startDate AND :endDate " +
           "AND a.actualWorkHours IS NOT NULL " +
           "ORDER BY a.workDate ASC")
    List<Attendance> findWithActualWorkHoursByUserIdAndWorkDateBetween(
            @Param("userId") Integer userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
