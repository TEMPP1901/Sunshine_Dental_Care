package sunshine_dental_care.services.impl.hr.face;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.entities.EmployeeFaceProfile;
import sunshine_dental_care.entities.FaceProfileUpdateRequest;
import sunshine_dental_care.repositories.hr.EmployeeFaceProfileRepo;
import sunshine_dental_care.repositories.hr.FaceProfileUpdateRequestRepo;
import sunshine_dental_care.services.interfaces.hr.FaceProfileUpdateService;
import sunshine_dental_care.services.impl.notification.NotificationService;
import sunshine_dental_care.dto.notificationDTO.NotificationRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaceProfileUpdateServiceImpl implements FaceProfileUpdateService {

    private final FaceProfileUpdateRequestRepo requestRepo;
    private final EmployeeFaceProfileRepo faceProfileRepo;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public FaceProfileUpdateRequest submitUpdateRequest(Integer userId, String faceEmbedding, String faceImageUrl) {
        log.info("Submitting face profile update request for user {}", userId);

        // User phải có face profile mới được gửi yêu cầu cập nhật
        if (!faceProfileRepo.existsByUserId(userId)) {
            throw new IllegalArgumentException(
                "User does not have a face profile. Please register first using /api/hr/face-profile/register");
        }

        // Chỉ cho phép có 1 request pending tại 1 thời điểm
        if (requestRepo.existsByUserIdAndStatus(userId, FaceProfileUpdateRequest.RequestStatus.PENDING)) {
            throw new IllegalStateException(
                "You already have a pending face profile update request. Please wait for HR approval.");
        }

        FaceProfileUpdateRequest request = FaceProfileUpdateRequest.builder()
            .userId(userId)
            .newFaceEmbedding(faceEmbedding)
            .newFaceImageUrl(faceImageUrl)
            .status(FaceProfileUpdateRequest.RequestStatus.PENDING)
            .requestedAt(LocalDateTime.now())
            .build();

        FaceProfileUpdateRequest saved = requestRepo.save(request);
        log.info("Face profile update request submitted successfully: requestId={}, userId={}", saved.getRequestId(), userId);
        return saved;
    }

    @Override
    @Transactional
    public FaceProfileUpdateRequest approveRequest(Integer requestId, Integer hrUserId) {
        log.info("Approving face profile update request: requestId={}, approvedBy={}", requestId, hrUserId);

        FaceProfileUpdateRequest request = requestRepo.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Face profile update request not found: " + requestId));

        if (request.getStatus() != FaceProfileUpdateRequest.RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not pending. Current status: " + request.getStatus());
        }

        // Thực hiện cập nhật vào bảng profile
        EmployeeFaceProfile profile = faceProfileRepo.findByUserId(request.getUserId())
            .orElse(new EmployeeFaceProfile());
        profile.setUserId(request.getUserId());
        profile.setFaceEmbedding(request.getNewFaceEmbedding());
        profile.setFaceImageUrl(request.getNewFaceImageUrl());
        faceProfileRepo.save(profile);

        request.setStatus(FaceProfileUpdateRequest.RequestStatus.APPROVED);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewedBy(hrUserId);

        FaceProfileUpdateRequest saved = requestRepo.save(request);
        log.info("Face profile update request approved: requestId={}, userId={}", requestId, request.getUserId());

        // Gửi thông báo đến user khi được duyệt
        try {
            notificationService.sendNotification(NotificationRequest.builder()
                .userId(request.getUserId())
                .type("FACE_PROFILE_APPROVED")
                .priority("HIGH")
                .title("Face Profile Update Approved")
                .message("Your face profile update request has been approved.")
                .relatedEntityType("FaceProfileUpdateRequest")
                .relatedEntityId(requestId)
                .build());
        } catch (Exception e) {
            log.error("Failed to send notification for face profile approval: {}", e.getMessage());
        }

        return saved;
    }

    @Override
    @Transactional
    public FaceProfileUpdateRequest rejectRequest(Integer requestId, Integer hrUserId, String reason) {
        log.info("Rejecting face profile update request: requestId={}, rejectedBy={}, reason={}", requestId, hrUserId, reason);

        FaceProfileUpdateRequest request = requestRepo.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Face profile update request not found: " + requestId));

        if (request.getStatus() != FaceProfileUpdateRequest.RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not pending. Current status: " + request.getStatus());
        }

        // Cập nhật trạng thái sang rejected
        request.setStatus(FaceProfileUpdateRequest.RequestStatus.REJECTED);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewedBy(hrUserId);
        request.setRejectionReason(reason);

        FaceProfileUpdateRequest saved = requestRepo.save(request);
        log.info("Face profile update request rejected: requestId={}, userId={}", requestId, request.getUserId());

        // Gửi notification khi bị từ chối
        try {
            notificationService.sendNotification(NotificationRequest.builder()
                .userId(request.getUserId())
                .type("FACE_PROFILE_REJECTED")
                .priority("HIGH")
                .title("Face Profile Update Rejected")
                .message("Your face profile update request has been rejected. Reason: " + reason)
                .relatedEntityType("FaceProfileUpdateRequest")
                .relatedEntityId(requestId)
                .build());
        } catch (Exception e) {
            log.error("Failed to send notification for face profile rejection: {}", e.getMessage());
        }

        return saved;
    }

    @Override
    public FaceProfileUpdateRequest getRequestByUserId(Integer userId) {
        return requestRepo.findByUserId(userId)
            .orElse(null);
    }

    @Override
    public List<FaceProfileUpdateRequest> getPendingRequests() {
        return requestRepo.findByStatusOrderByRequestedAtDesc(FaceProfileUpdateRequest.RequestStatus.PENDING);
    }

    @Override
    public boolean hasPendingRequest(Integer userId) {
        return requestRepo.existsByUserIdAndStatus(userId, FaceProfileUpdateRequest.RequestStatus.PENDING);
    }

    @Override
    @Transactional
    public void registerFaceProfile(Integer userId, String faceEmbedding, String faceImageUrl) {
        log.info("Registering face profile for user {} (first time registration)", userId);

        // Chỉ cho phép đăng ký nếu user chưa có profile
        if (faceProfileRepo.existsByUserId(userId)) {
            throw new IllegalStateException("User already has a face profile. Use update request for changes.");
        }

        EmployeeFaceProfile profile = new EmployeeFaceProfile();
        profile.setUserId(userId);
        profile.setFaceEmbedding(faceEmbedding);
        profile.setFaceImageUrl(faceImageUrl);
        faceProfileRepo.save(profile);

        log.info("Face profile registered successfully for user {}", userId);
    }
}
