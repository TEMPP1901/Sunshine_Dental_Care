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
    
    // Lấy tất cả leave request của user
    List<LeaveRequest> findByUserIdOrderByCreatedAtDesc(Integer userId);
    
    // Lấy leave request của user với phân trang
    Page<LeaveRequest> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);
    
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
    
    // Lấy tất cả leave request pending (cho HR duyệt)
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = 'PENDING' ORDER BY lr.createdAt DESC")
    List<LeaveRequest> findAllPending();
    
    // Lấy leave request pending với phân trang
    Page<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
}

