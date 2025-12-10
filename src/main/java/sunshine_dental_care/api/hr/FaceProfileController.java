package sunshine_dental_care.api.hr;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.entities.FaceProfileUpdateRequest;
import sunshine_dental_care.repositories.hr.EmployeeFaceProfileRepo;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.interfaces.hr.FaceProfileUpdateService;
import sunshine_dental_care.services.upload_file.AvatarStorageService;

@RestController
@RequestMapping("/api/hr/face-profile")
@RequiredArgsConstructor
@Slf4j
public class FaceProfileController {
    
    private final FaceProfileUpdateService faceProfileUpdateService;
    private final AvatarStorageService avatarStorageService;
    private final EmployeeFaceProfileRepo faceProfileRepo;
    
    // Nhân viên đăng ký face profile lần đầu (bắt buộc khi đăng nhập lần đầu, không cần duyệt)
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registerFaceProfile(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("User not authenticated");
        }
        
        Integer userId = currentUser.userId();
        log.info("Face profile registration request from user {}", userId);
        
        try {
            // Upload avatar và extract embedding (AvatarStorageService sẽ tự động lưu vào EmployeeFaceProfile)
            String avatarUrl = avatarStorageService.storeAvatar(file, userId);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Face profile registered successfully");
            response.put("avatarUrl", avatarUrl);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to register face profile for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to register face profile: " + e.getMessage(), e);
        }
    }
    
    // Nhân viên gửi yêu cầu cập nhật face profile (cần HR duyệt)
    @PostMapping("/update-request")
    public ResponseEntity<Map<String, String>> submitUpdateRequest(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("User not authenticated");
        }
        
        Integer userId = currentUser.userId();
        log.info("Face profile update request from user {}", userId);
        
        try {
            // Upload avatar và extract embedding (không lưu vào EmployeeFaceProfile)
            java.util.Map<String, String> uploadResult = avatarStorageService.storeAvatarAndExtractEmbedding(file, userId);
            String tempAvatarUrl = uploadResult.get("avatarUrl");
            String embedding = uploadResult.get("embedding");
            
            // Tạo yêu cầu cập nhật
            faceProfileUpdateService.submitUpdateRequest(userId, embedding, tempAvatarUrl);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Update request submitted successfully. Waiting for HR approval.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to submit update request for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to submit update request: " + e.getMessage(), e);
        }
    }
    
    // HR: Lấy danh sách yêu cầu đang chờ duyệt
    @GetMapping("/pending-requests")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<List<FaceProfileUpdateRequest>> getPendingRequests() {
        List<FaceProfileUpdateRequest> requests = faceProfileUpdateService.getPendingRequests();
        return ResponseEntity.ok(requests);
    }
    
    // HR: Duyệt yêu cầu cập nhật
    @PostMapping("/approve/{requestId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<Map<String, String>> approveRequest(
            @PathVariable Integer requestId,
            @AuthenticationPrincipal CurrentUser currentUser) {
        
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("User not authenticated");
        }
        
        faceProfileUpdateService.approveRequest(requestId, currentUser.userId());
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Face profile update request approved successfully");
        return ResponseEntity.ok(response);
    }
    
    // HR: Từ chối yêu cầu cập nhật
    @PostMapping("/reject/{requestId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<Map<String, String>> rejectRequest(
            @PathVariable Integer requestId,
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(required = false) String reason) {
        
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("User not authenticated");
        }
        
        faceProfileUpdateService.rejectRequest(requestId, currentUser.userId(), reason);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Face profile update request rejected");
        return ResponseEntity.ok(response);
    }
    
    // Kiểm tra user đã đăng ký face profile chưa
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkFaceProfile(
            @AuthenticationPrincipal CurrentUser currentUser) {
        
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("User not authenticated");
        }
        
        Integer userId = currentUser.userId();
        boolean hasFaceProfile = faceProfileRepo.existsByUserId(userId);
        boolean hasPendingRequest = faceProfileUpdateService.hasPendingRequest(userId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("hasFaceProfile", hasFaceProfile);
        response.put("hasPendingRequest", hasPendingRequest);
        response.put("requiresRegistration", !hasFaceProfile);
        
        return ResponseEntity.ok(response);
    }
}

