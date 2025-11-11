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
    
    /**
     * Tìm attendance của user trong ngày cụ thể
     */
    Optional<Attendance> findByUserIdAndWorkDate(Integer userId, LocalDate workDate);
    
    /**
     * Tìm attendance của user tại clinic trong ngày cụ thể
     */
    Optional<Attendance> findByUserIdAndClinicIdAndWorkDate(Integer userId, Integer clinicId, LocalDate workDate);
    
    /**
     * Lấy lịch sử attendance của user trong khoảng thời gian
     */
    List<Attendance> findByUserIdAndWorkDateBetweenOrderByWorkDateDesc(
        Integer userId, LocalDate startDate, LocalDate endDate);
    
    /**
     * Lấy lịch sử attendance của user tại clinic trong khoảng thời gian
     */
    List<Attendance> findByUserIdAndClinicIdAndWorkDateBetweenOrderByWorkDateDesc(
        Integer userId, Integer clinicId, LocalDate startDate, LocalDate endDate);
    
    /**
     * Lấy danh sách attendance của clinic trong ngày
     */
    List<Attendance> findByClinicIdAndWorkDate(Integer clinicId, LocalDate workDate);
    
    /**
     * Lấy danh sách attendance của clinic trong khoảng thời gian (có phân trang)
     */
    Page<Attendance> findByClinicIdAndWorkDateBetween(
        Integer clinicId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Attendance> findByWorkDate(LocalDate workDate);

    List<Attendance> findByWorkDateAndAttendanceStatus(LocalDate workDate, String attendanceStatus);

    List<Attendance> findByClinicIdAndWorkDateAndAttendanceStatus(Integer clinicId, LocalDate workDate, String attendanceStatus);
    
    /**
     * Đếm số lần chấm công của user trong khoảng thời gian
     */
    long countByUserIdAndWorkDateBetween(Integer userId, LocalDate startDate, LocalDate endDate);
    
    /**
     * Đếm số lần chấm công của user tại clinic trong khoảng thời gian
     */
    long countByUserIdAndClinicIdAndWorkDateBetween(
        Integer userId, Integer clinicId, LocalDate startDate, LocalDate endDate);
    
    /**
     * Kiểm tra user đã check-in trong ngày chưa
     */
    @Query("SELECT COUNT(a) > 0 FROM Attendance a WHERE a.userId = :userId AND a.workDate = :workDate AND a.checkInTime IS NOT NULL")
    boolean existsByUserIdAndWorkDateWithCheckIn(@Param("userId") Integer userId, @Param("workDate") LocalDate workDate);
    
    /**
     * Kiểm tra user đã check-out trong ngày chưa
     */
    @Query("SELECT COUNT(a) > 0 FROM Attendance a WHERE a.userId = :userId AND a.workDate = :workDate AND a.checkOutTime IS NOT NULL")
    boolean existsByUserIdAndWorkDateWithCheckOut(@Param("userId") Integer userId, @Param("workDate") LocalDate workDate);
}

