package sunshine_dental_care.services.impl.notification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.notificationDTO.NotificationRequest;
import sunshine_dental_care.dto.notificationDTO.NotificationResponse;
import sunshine_dental_care.dto.notificationDTO.NotificationStatistics;
import sunshine_dental_care.entities.Log;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserDevice;
import sunshine_dental_care.repositories.NotificationRepository;
import sunshine_dental_care.repositories.UserDeviceRepo;
import sunshine_dental_care.repositories.auth.UserRepo;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserDeviceRepo userDeviceRepo;
    private final UserRepo userRepo;
    private final FirestoreService firestoreService;
    private final NotificationAsyncService notificationAsyncService;

    // Gửi thông báo qua DB, sau khi commit thì mới gửi qua WebSocket, Firestore, FCM
    @Transactional
    public NotificationResponse sendNotification(NotificationRequest request) {
        if (request.getUserId() == null) {
            log.warn("Notification request missing userId");
            throw new IllegalArgumentException("UserId is required");
        }

        if (!userRepo.existsById(request.getUserId())) {
            throw new RuntimeException("User not found: " + request.getUserId());
        }

        // Tạo đối tượng notification, liên kết user entity
        User user = userRepo.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserId()));

        Log notification = Log.builder()
                .user(user)
                .type(request.getType())
                .priority(request.getPriority() != null ? request.getPriority() : "MEDIUM")
                .title(request.getTitle())
                .message(request.getMessage())
                .actionUrl(request.getActionUrl())
                .relatedEntityType(request.getRelatedEntityType())
                .relatedEntityId(request.getRelatedEntityId())
                .isRead(false)
                .createdAt(Instant.now())
                .expiresAt(request.getExpiresAt())
                .build();

        notification = notificationRepository.save(notification);
        log.info("Notification saved to SQL Server (Logs table) - ID: {}, UserId: {}",
                notification.getId(), request.getUserId());

        NotificationResponse response = toResponse(notification);

        // Đảm bảo chỉ gửi thông báo realtime sau khi transaction đã commit thành công
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    notificationAsyncService.distributeNotification(response, String.valueOf(request.getUserId()));
                } catch (Exception e) {
                    log.error("Failed to trigger async notification distribution: {}", e.getMessage(), e);
                }
            }
        });

        return response;
    }

    // Đăng ký (hoặc cập nhật) thiết bị FCM cho user
    @Transactional
    public void registerDevice(Integer userId, String token, String deviceType) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserDevice device = userDeviceRepo.findByUserIdAndFcmToken(userId, token)
                .orElse(UserDevice.builder()
                        .user(user)
                        .fcmToken(token)
                        .build());

        device.setDeviceType(deviceType);
        device.setLastActive(Instant.now());
        userDeviceRepo.save(device);
    }

    // Lấy danh sách notification cho user (có thể lấy hết hoặc chỉ active), phân trang
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Integer userId, int page, int size, boolean includeExpired) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Log> notifications;
        if (includeExpired) {
            notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        } else {
            notifications = notificationRepository.findActiveNotificationsByUserId(userId, pageable);
        }
        return notifications.map(this::toResponse);
    }

    // Lấy danh sách notification chưa đọc
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(Integer userId) {
        List<Log> notifications = notificationRepository.findUnreadNotifications(userId);
        return notifications.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Đánh dấu 1 notification là đã đọc, cập nhật cả Firestore
    @Transactional
    public NotificationResponse markAsRead(Integer notificationId, Integer userId) {
        Log notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        // Kiểm tra quyền user sở hữu notification này
        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("Notification does not belong to user");
        }

        notification.setIsRead(true);
        notification.setReadAt(Instant.now());
        notification = notificationRepository.save(notification);

        // Gửi cập nhật lên Firestore, không throw lỗi để không làm rollback transaction
        try {
            firestoreService.markAsRead(userId, notificationId);
        } catch (Exception e) {
            log.error("[Firestore] Error marking as read in Firestore: {}", e.getMessage());
        }

        return toResponse(notification);
    }

    // Đánh dấu tất cả là đã đọc
    @Transactional
    public void markAllAsRead(Integer userId) {
        notificationRepository.markAllAsRead(userId);
    }

    @Transactional(readOnly = true)
    public long countUnread(Integer userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    // Thống kê số lượng các loại notification cho user
    @Transactional(readOnly = true)
    public NotificationStatistics getStatistics(Integer userId) {
        long total = notificationRepository.countTotalByUserId(userId);
        long unread = notificationRepository.countByUserIdAndIsReadFalse(userId);
        long read = notificationRepository.countReadByUserId(userId);
        long highPriority = notificationRepository.countByUserIdAndPriority(userId, "HIGH");
        long mediumPriority = notificationRepository.countByUserIdAndPriority(userId, "MEDIUM");
        long lowPriority = notificationRepository.countByUserIdAndPriority(userId, "LOW");
        long expired = notificationRepository.countExpiredByUserId(userId);
        long today = notificationRepository.countTodayByUserId(userId);

        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long thisWeek = notificationRepository.countByUserIdAndCreatedAtAfter(userId, weekAgo);

        Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        long thisMonth = notificationRepository.countByUserIdAndCreatedAtAfter(userId, monthAgo);

        return NotificationStatistics.builder()
                .totalNotifications(total)
                .unreadCount(unread)
                .readCount(read)
                .highPriorityCount(highPriority)
                .mediumPriorityCount(mediumPriority)
                .lowPriorityCount(lowPriority)
                .expiredCount(expired)
                .todayCount(today)
                .thisWeekCount(thisWeek)
                .thisMonthCount(thisMonth)
                .build();
    }

    // Trả về danh sách thông báo mới kể từ lần đồng bộ gần nhất
    @Transactional(readOnly = true)
    public List<NotificationResponse> syncNotifications(Integer userId, Instant lastSyncTime) {
        List<Log> notifications = notificationRepository.findNewNotificationsSince(userId, lastSyncTime);
        return notifications.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Convert Log entity sang NotificationResponse DTO
    private NotificationResponse toResponse(Log notification) {
        return NotificationResponse.builder()
                .notificationId(notification.getId())
                .userId(notification.getUserId())
                .type(notification.getType())
                .priority(notification.getPriority())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .actionUrl(notification.getActionUrl())
                .relatedEntityType(notification.getRelatedEntityType())
                .relatedEntityId(notification.getRelatedEntityId())
                .createdAt(notification.getCreatedAt())
                .expiresAt(notification.getExpiresAt())
                .build();
    }
}
