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
    
    // Lấy tất cả leave request của user (với fetch join để tránh lazy loading)
    @Query("SELECT lr FROM LeaveRequest lr " +
           "LEFT JOIN FETCH lr.user " +
           "LEFT JOIN FETCH lr.clinic " +
           "LEFT JOIN FETCH lr.approvedBy " +
           "WHERE lr.user.id = :userId " +
           "ORDER BY lr.createdAt DESC")
    List<LeaveRequest> findByUserIdOrderByCreatedAtDesc(@Param("userId") Integer userId);
    
    // Lấy leave request của user với phân trang
    // Note: Không dùng FETCH JOIN với Pageable, sẽ dùng separate query để load relationships
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.user.id = :userId ORDER BY lr.createdAt DESC")
    Page<LeaveRequest> findByUserIdOrderByCreatedAtDesc(@Param("userId") Integer userId, Pageable pageable);
    
    // Lấy leave request theo status
    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status);
    
    // Lấy leave request của user theo status
    List<LeaveRequest> findByUserIdAndStatusOrderByCreatedAtDesc(Integer userId, String status);
    
    // Lấy leave request trong khoảng thời gian
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.user.id = :userId " +
           "AND lr.startDate <= :endDate AND lr.endDate >= :startDate " +
           "ORDER BY lr.createdAt DESC")
    List<LeaveRequest> findByUserIdAndDateRange(
        @Param("userId") Integer userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);
    
    // Lấy leave request approved trong khoảng thời gian (dùng để check khi tạo schedule)
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.user.id = :userId " +
           "AND lr.status = 'APPROVED' " +
           "AND lr.startDate <= :endDate AND lr.endDate >= :startDate")
    List<LeaveRequest> findApprovedByUserIdAndDateRange(
        @Param("userId") Integer userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);
    
    // Kiểm tra user có leave request approved trong ngày cụ thể không
    @Query("SELECT COUNT(lr) > 0 FROM LeaveRequest lr WHERE lr.user.id = :userId " +
           "AND lr.status = 'APPROVED' " +
           "AND lr.startDate <= :date AND lr.endDate >= :date")
    boolean hasApprovedLeaveOnDate(
        @Param("userId") Integer userId,
        @Param("date") LocalDate date);
    

        // Kiểm tra user có leave request approved trong ngày và ca cụ thể không (cho bác sĩ)
    // Nếu shiftType là FULL_DAY hoặc null, check như hasApprovedLeaveOnDate
    // Nếu shiftType là MORNING/AFTERNOON, chỉ check leave request có shiftType tương ứng hoặc FULL_DAY
    @Query("SELECT COUNT(lr) > 0 FROM LeaveRequest lr WHERE lr.user.id = :userId " +
           "AND lr.status = 'APPROVED' " +
           "AND lr.startDate <= :date AND lr.endDate >= :date " +
           "AND (lr.shiftType IS NULL OR lr.shiftType = 'FULL_DAY' OR lr.shiftType = :shiftType)")
    boolean hasApprovedLeaveOnDateAndShift(
        @Param("userId") Integer userId,
        @Param("date") LocalDate date,
        @Param("shiftType") String shiftType);
    
    // Lấy tất cả leave request pending (cho HR duyệt)
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = 'PENDING' ORDER BY lr.createdAt DESC")
    List<LeaveRequest> findAllPending();
    
    // Lấy leave request pending với phân trang
    Page<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    
    //  Batch query: Lấy tất cả approved leave requests cho nhiều doctors trong khoảng thời gian
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.user.id IN :userIds " +
           "AND lr.status = 'APPROVED' " +
           "AND lr.startDate <= :endDate AND lr.endDate >= :startDate")
    List<LeaveRequest> findApprovedByUserIdsAndDateRange(
        @Param("userIds") List<Integer> userIds,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);
}

