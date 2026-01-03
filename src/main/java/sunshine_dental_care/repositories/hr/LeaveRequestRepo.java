package sunshine_dental_care.repositories.hr;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.LeaveRequest;

@Repository
public interface LeaveRequestRepo extends JpaRepository<LeaveRequest, Integer> {
       // Lấy tất cả đơn nghỉ việc của user (fetch join tránh lazy loading)
       @Query("SELECT lr FROM LeaveRequest lr " +
             "LEFT JOIN FETCH lr.user " +
             "LEFT JOIN FETCH lr.clinic " +
             "LEFT JOIN FETCH lr.approvedBy " +
             "WHERE lr.user.id = :userId " +
             "ORDER BY lr.createdAt DESC")
       List<LeaveRequest> findByUserIdOrderByCreatedAtDesc(@Param("userId") Integer userId);

       // Lấy đơn nghỉ việc của user phân trang
       @Query("SELECT lr FROM LeaveRequest lr WHERE lr.user.id = :userId ORDER BY lr.createdAt DESC")
       Page<LeaveRequest> findByUserIdOrderByCreatedAtDesc(@Param("userId") Integer userId, Pageable pageable);

       List<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status);

       List<LeaveRequest> findByUserIdAndStatusOrderByCreatedAtDesc(Integer userId, String status);

       // Lấy đơn nghỉ việc theo type + status (dùng kiểm tra đơn nghỉ việc đã nộp)
       @Query("SELECT lr FROM LeaveRequest lr " +
             "WHERE lr.user.id = :userId " +
             "AND UPPER(lr.type) = UPPER(:type) " +
             "AND lr.status = :status " +
             "ORDER BY lr.createdAt DESC")
       List<LeaveRequest> findByUserIdAndTypeAndStatusOrderByCreatedAtDesc(
             @Param("userId") Integer userId,
             @Param("type") String type,
             @Param("status") String status);

       // Lấy đơn nghỉ phép trong khoảng thời gian
       @Query("SELECT lr FROM LeaveRequest lr WHERE lr.user.id = :userId " +
             "AND lr.startDate <= :endDate AND lr.endDate >= :startDate " +
             "ORDER BY lr.createdAt DESC")
       List<LeaveRequest> findByUserIdAndDateRange(
             @Param("userId") Integer userId,
             @Param("startDate") LocalDate startDate,
             @Param("endDate") LocalDate endDate);

       // Lấy các đơn đã được duyệt của user trong khoảng thời gian (check trùng ca khi xếp lịch)
       @Query("SELECT lr FROM LeaveRequest lr WHERE lr.user.id = :userId " +
             "AND lr.status = 'APPROVED' " +
             "AND lr.startDate <= :endDate AND lr.endDate >= :startDate")
       List<LeaveRequest> findApprovedByUserIdAndDateRange(
             @Param("userId") Integer userId,
             @Param("startDate") LocalDate startDate,
             @Param("endDate") LocalDate endDate);

       // Kiểm tra user đã có đơn nghỉ được duyệt trong ngày đó chưa
       @Query("SELECT COUNT(lr) > 0 FROM LeaveRequest lr WHERE lr.user.id = :userId " +
             "AND lr.status = 'APPROVED' " +
             "AND lr.startDate <= :date AND lr.endDate >= :date")
       boolean hasApprovedLeaveOnDate(
             @Param("userId") Integer userId,
             @Param("date") LocalDate date);

       // Kiểm tra đơn đã duyệt trùng ca/ngày (dạng shift cho bác sĩ)
       @Query("SELECT COUNT(lr) > 0 FROM LeaveRequest lr WHERE lr.user.id = :userId " +
             "AND lr.status = 'APPROVED' " +
             "AND lr.startDate <= :date AND lr.endDate >= :date " +
             "AND (lr.shiftType IS NULL OR lr.shiftType = 'FULL_DAY' OR lr.shiftType = :shiftType)")
       boolean hasApprovedLeaveOnDateAndShift(
             @Param("userId") Integer userId,
             @Param("date") LocalDate date,
             @Param("shiftType") String shiftType);

       // Lấy toàn bộ đơn nghỉ phép trạng thái PENDING (dành cho HR duyệt)
       @Query("SELECT lr FROM LeaveRequest lr " +
             "LEFT JOIN FETCH lr.user " +
             "LEFT JOIN FETCH lr.clinic " +
             "LEFT JOIN FETCH lr.approvedBy " +
             "WHERE lr.status = 'PENDING' " +
             "ORDER BY lr.createdAt DESC")
       List<LeaveRequest> findAllPending();

       // Lấy toàn bộ đơn nghỉ phép trạng thái PENDING_ADMIN (dành cho Admin duyệt)
       @Query("SELECT lr FROM LeaveRequest lr " +
             "LEFT JOIN FETCH lr.user " +
             "LEFT JOIN FETCH lr.clinic " +
             "LEFT JOIN FETCH lr.approvedBy " +
             "WHERE lr.status = 'PENDING_ADMIN' " +
             "ORDER BY lr.createdAt DESC")
       List<LeaveRequest> findAllPendingAdmin();

       // Lấy danh sách đơn nghỉ với phân trang theo status (cho màn chờ duyệt)
       @Query(value = "SELECT lr FROM LeaveRequest lr " +
             "LEFT JOIN FETCH lr.user " +
             "LEFT JOIN FETCH lr.clinic " +
             "LEFT JOIN FETCH lr.approvedBy " +
             "WHERE lr.status = :status " +
             "ORDER BY lr.createdAt DESC", countQuery = "SELECT COUNT(lr) FROM LeaveRequest lr WHERE lr.status = :status")
       Page<LeaveRequest> findByStatusOrderByCreatedAtDesc(@Param("status") String status, Pageable pageable);

       // Lấy các đơn đã duyệt của nhiều user trong một khoảng thời gian
       @Query("SELECT lr FROM LeaveRequest lr WHERE lr.user.id IN :userIds " +
             "AND lr.status = 'APPROVED' " +
             "AND lr.startDate <= :endDate AND lr.endDate >= :startDate")
       List<LeaveRequest> findApprovedByUserIdsAndDateRange(
             @Param("userIds") List<Integer> userIds,
             @Param("startDate") LocalDate startDate,
             @Param("endDate") LocalDate endDate);

       // Lấy toàn bộ đơn đã duyệt trong khoảng thời gian (toàn phòng - không cần userIds)
       @Query("SELECT lr FROM LeaveRequest lr " +
             "LEFT JOIN FETCH lr.user " +
             "LEFT JOIN FETCH lr.clinic " +
             "LEFT JOIN FETCH lr.approvedBy " +
             "WHERE lr.status = 'APPROVED' " +
             "AND lr.startDate <= :endDate AND lr.endDate >= :startDate " +
             "ORDER BY lr.startDate ASC")
       List<LeaveRequest> findApprovedByDateRange(
             @Param("startDate") LocalDate startDate,
             @Param("endDate") LocalDate endDate);

       // Đếm tổng số ngày nghỉ đã được duyệt trong năm cho 1 user
       @Query("SELECT COALESCE(SUM(DATEDIFF(day, lr.startDate, lr.endDate) + 1), 0) FROM LeaveRequest lr " +
             "WHERE lr.user.id = :userId " +
             "AND lr.status = 'APPROVED' " +
             "AND YEAR(lr.startDate) = :year")
       Integer countApprovedLeaveDays(@Param("userId") Integer userId, @Param("year") int year);

       // Đếm tổng số ngày nghỉ đã được duyệt trong 1 tháng chỉ định
       @Query("SELECT COALESCE(SUM(DATEDIFF(day, lr.startDate, lr.endDate) + 1), 0) FROM LeaveRequest lr " +
             "WHERE lr.user.id = :userId " +
             "AND lr.status = 'APPROVED' " +
             "AND YEAR(lr.startDate) = :year " +
             "AND MONTH(lr.startDate) = :month")
       Integer countApprovedLeaveDaysInMonth(@Param("userId") Integer userId, @Param("year") int year, @Param("month") int month);

       // Đếm số lượng đơn theo status
       long countByStatus(String status);
}
