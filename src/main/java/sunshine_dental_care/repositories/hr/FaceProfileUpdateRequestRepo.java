package sunshine_dental_care.repositories.hr;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.FaceProfileUpdateRequest;

@Repository
public interface FaceProfileUpdateRequestRepo extends JpaRepository<FaceProfileUpdateRequest, Integer> {
    
    // Tìm yêu cầu cập nhật face profile bằng userId
    Optional<FaceProfileUpdateRequest> findByUserId(Integer userId);
    
    // Tìm yêu cầu đang chờ duyệt (PENDING) của một user
    Optional<FaceProfileUpdateRequest> findByUserIdAndStatus(Integer userId, FaceProfileUpdateRequest.RequestStatus status);
    
    // Lấy tất cả yêu cầu đang chờ duyệt (với fetch join để lấy thông tin user)
    @Query("SELECT r FROM FaceProfileUpdateRequest r " +
           "LEFT JOIN FETCH r.user " +
           "WHERE r.status = :status " +
           "ORDER BY r.requestedAt DESC")
    List<FaceProfileUpdateRequest> findByStatusOrderByRequestedAtDesc(@Param("status") FaceProfileUpdateRequest.RequestStatus status);
    
    // Kiểm tra user có yêu cầu đang chờ duyệt không
    boolean existsByUserIdAndStatus(Integer userId, FaceProfileUpdateRequest.RequestStatus status);
}

