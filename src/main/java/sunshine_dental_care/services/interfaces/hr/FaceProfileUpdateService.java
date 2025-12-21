package sunshine_dental_care.services.interfaces.hr;

import java.util.List;

import sunshine_dental_care.entities.FaceProfileUpdateRequest;

public interface FaceProfileUpdateService {
    
    // Nhân viên gửi yêu cầu cập nhật face profile
    FaceProfileUpdateRequest submitUpdateRequest(Integer userId, String faceEmbedding, String faceImageUrl);
    
    // HR duyệt yêu cầu cập nhật face profile
    FaceProfileUpdateRequest approveRequest(Integer requestId, Integer hrUserId);
    
    // HR từ chối yêu cầu cập nhật face profile
    FaceProfileUpdateRequest rejectRequest(Integer requestId, Integer hrUserId, String reason);
    
    // Lấy yêu cầu cập nhật của một user
    FaceProfileUpdateRequest getRequestByUserId(Integer userId);
    
    // Lấy tất cả yêu cầu đang chờ duyệt (cho HR)
    List<FaceProfileUpdateRequest> getPendingRequests();
    
    // Kiểm tra user có yêu cầu đang chờ duyệt không
    boolean hasPendingRequest(Integer userId);
    
    // Nhân viên đăng ký face profile lần đầu (không cần duyệt)
    void registerFaceProfile(Integer userId, String faceEmbedding, String faceImageUrl);
}

