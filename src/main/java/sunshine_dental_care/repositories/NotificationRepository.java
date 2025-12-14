package sunshine_dental_care.repositories;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.Log;

@Repository
public interface NotificationRepository extends JpaRepository<Log, Integer> {
    // Lấy các thông báo còn hiệu lực cho người dùng
    @Query("SELECT n FROM Log n WHERE n.userId = :userId " +
           "AND (n.expiresAt IS NULL OR n.expiresAt > CURRENT_TIMESTAMP) " +
           "ORDER BY n.createdAt DESC")
    Page<Log> findActiveNotificationsByUserId(@Param("userId") Integer userId, Pageable pageable);

    Page<Log> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);

    // Đếm số thông báo chưa đọc (chỉ thông báo còn hiệu lực) cho người dùng
    @Query("SELECT COUNT(n) FROM Log n WHERE n.userId = :userId " +
           "AND n.isRead = false " +
           "AND (n.expiresAt IS NULL OR n.expiresAt > CURRENT_TIMESTAMP)")
    long countByUserIdAndIsReadFalse(@Param("userId") Integer userId);

    // Đếm tất cả thông báo chưa đọc (bao gồm hết hạn) cho thống kê
    @Query("SELECT COUNT(n) FROM Log n WHERE n.userId = :userId AND n.isRead = false")
    long countAllUnreadByUserId(@Param("userId") Integer userId);

    // Đánh dấu tất cả thông báo là đã đọc cho user
    @Modifying
    @Query("UPDATE Log n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP " +
           "WHERE n.userId = :userId AND n.isRead = false")
    void markAllAsRead(@Param("userId") Integer userId);

    // Thống kê tổng số thông báo của user
    @Query("SELECT COUNT(n) FROM Log n WHERE n.userId = :userId")
    long countTotalByUserId(@Param("userId") Integer userId);

    // Thống kê số thông báo đã đọc của user
    @Query("SELECT COUNT(n) FROM Log n WHERE n.userId = :userId AND n.isRead = true")
    long countReadByUserId(@Param("userId") Integer userId);

    // Thống kê thông báo theo mức độ ưu tiên
    @Query("SELECT COUNT(n) FROM Log n WHERE n.userId = :userId AND n.priority = :priority")
    long countByUserIdAndPriority(@Param("userId") Integer userId, @Param("priority") String priority);

    // Đếm số thông báo đã hết hạn cho user
    @Query("SELECT COUNT(n) FROM Log n WHERE n.userId = :userId " +
           "AND n.expiresAt IS NOT NULL AND n.expiresAt < CURRENT_TIMESTAMP")
    long countExpiredByUserId(@Param("userId") Integer userId);

    // Đếm số thông báo trong ngày cho user
    @Query(value = "SELECT COUNT(n) FROM Logs n WHERE n.userId = :userId " +
           "AND CAST(n.createdAt AS DATE) = CAST(GETDATE() AS DATE)", 
           nativeQuery = true)
    long countTodayByUserId(@Param("userId") Integer userId);

    // Đếm số thông báo được tạo sau một ngày xác định
    @Query("SELECT COUNT(n) FROM Log n WHERE n.userId = :userId " +
           "AND n.createdAt >= :startDate")
    long countByUserIdAndCreatedAtAfter(@Param("userId") Integer userId, @Param("startDate") Instant startDate);

    // Lấy các thông báo mới kể từ thời điểm đồng bộ cuối
    @Query("SELECT n FROM Log n WHERE n.userId = :userId " +
           "AND n.createdAt > :lastSyncTime " +
           "ORDER BY n.createdAt DESC")
    List<Log> findNewNotificationsSince(@Param("userId") Integer userId, @Param("lastSyncTime") Instant lastSyncTime);

    // Lấy các thông báo chưa đọc cho người dùng (offline)
    @Query("SELECT n FROM Log n WHERE n.userId = :userId " +
           "AND n.isRead = false " +
           "AND (n.expiresAt IS NULL OR n.expiresAt > CURRENT_TIMESTAMP) " +
           "ORDER BY n.createdAt DESC")
    List<Log> findUnreadNotifications(@Param("userId") Integer userId);

    // Kiểm tra xem đã có notification với type và relatedEntityId cụ thể trong ngày hôm nay chưa
    @Query(value = "SELECT COUNT(n) FROM Logs n WHERE n.userId = :userId " +
           "AND n.type = :type " +
           "AND n.relatedEntityType = :relatedEntityType " +
           "AND n.relatedEntityId = :relatedEntityId " +
           "AND CAST(n.createdAt AS DATE) = CAST(GETDATE() AS DATE)",
           nativeQuery = true)
    long countByUserIdAndTypeAndEntityToday(
        @Param("userId") Integer userId,
        @Param("type") String type,
        @Param("relatedEntityType") String relatedEntityType,
        @Param("relatedEntityId") Integer relatedEntityId
    );
}
