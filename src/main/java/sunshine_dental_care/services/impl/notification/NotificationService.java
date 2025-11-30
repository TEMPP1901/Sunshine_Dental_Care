package sunshine_dental_care.services.impl.notification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final FCMService fcmService;
    private final SimpMessagingTemplate messagingTemplate;
    private final FirestoreService firestoreService;

    // Gửi thông báo đến user qua DB, WebSocket, Firestore và FCM
    @Transactional
    public NotificationResponse sendNotification(NotificationRequest request) {
        if (request.getUserId() == null) {
            log.warn("Notification request missing userId");
            throw new IllegalArgumentException("UserId is required");
        }

        // Kiểm tra sự tồn tại của user
        if (!userRepo.existsById(request.getUserId())) {
            throw new RuntimeException("User not found: " + request.getUserId());
        }

        Log notification = null;
        
        // Lưu thông báo vào cơ sở dữ liệu SQL Server
        try {
            notification = Log.builder()
                    .userId(request.getUserId())
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
        } catch (Exception e) {
            log.error("Failed to save notification to SQL Server: {}", e.getMessage(), e);
        }

        // Đẩy thông báo qua WebSocket cho web client realtime
        if (notification != null) {
            try {
                String userId = String.valueOf(request.getUserId());
                NotificationResponse response = toResponse(notification);
                
                // Destination sẽ là /user/{userId}/queue/notifications (Spring tự động thêm /user/ prefix)
                String destination = "/user/" + userId + "/queue/notifications";
                log.info("[WebSocket] Preparing to send notification to user {} at destination {}", 
                        userId, destination);

                messagingTemplate.convertAndSendToUser(
                        userId,
                        "/queue/notifications",
                        response);

                log.info("[WebSocket] Notification sent successfully to user {} at destination {} (full path: {})",
                        userId, "/queue/notifications", destination);
            } catch (Exception e) {
                log.error("[WebSocket] Error sending WebSocket message to user {}: {}", 
                        request.getUserId(), e.getMessage(), e);
            }
        }

        // Lưu thông báo lên Firestore cho mobile realtime
        if (notification != null) {
            try {
                NotificationResponse response = toResponse(notification);
                firestoreService.saveNotification(response);
                log.info("[Firestore] Notification saved to Firestore for user: {}", request.getUserId());
            } catch (Exception e) {
                log.error("[Firestore] Error saving to Firestore: {}", e.getMessage(), e);
            }
        }

        // Gửi thông báo qua FCM cho các thiết bị di động
        log.info("Start FCM notification process for user: {}", request.getUserId());
        try {
            List<UserDevice> devices = userDeviceRepo.findByUserId(request.getUserId());
            log.info("Found {} device(s) for user: {}", devices.size(), request.getUserId());
            
            if (devices.isEmpty()) {
                log.warn("No FCM devices found for user: {}. User may not have logged in on mobile app yet.", 
                        request.getUserId());
            } else {
                int successCount = 0;
                for (UserDevice device : devices) {
                    try {
                        fcmService.sendNotification(
                                device.getFcmToken(),
                                request.getTitle(),
                                request.getMessage(),
                                request.getActionUrl(),
                                request.getRelatedEntityType(),
                                request.getRelatedEntityId());
                        successCount++;
                        log.info("FCM notification sent successfully to device: {} (type: {})", 
                                device.getId(), device.getDeviceType());
                    } catch (Exception e) {
                        log.error("Failed to send FCM to device {} (type: {}): {}", 
                                device.getId(), device.getDeviceType(), e.getMessage(), e);
                    }
                }
                log.info("FCM notifications summary: {}/{} devices sent successfully for user: {}", 
                        successCount, devices.size(), request.getUserId());
            }
        } catch (Exception e) {
            log.error("Error sending FCM notifications: {}", e.getMessage(), e);
        }

        // Trả về response, nếu lưu DB thất bại thì dùng response tạm
        if (notification == null) {
            log.warn("Returning notification response without DB record (DB save failed)");
            return NotificationResponse.builder()
                    .userId(request.getUserId())
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
        }

        return toResponse(notification);
    }

    // Đăng ký thiết bị nhận thông báo FCM cho 1 user
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

    // Lấy danh sách thông báo cho user với phân trang và có thể bao gồm hết hoặc chỉ đang hoạt động
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

    // Lấy danh sách thông báo chưa đọc của user
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(Integer userId) {
        List<Log> notifications = notificationRepository.findUnreadNotifications(userId);
        return notifications.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Đánh dấu 1 thông báo là đã đọc và cập nhật trạng thái trên Firestore
    @Transactional
    public NotificationResponse markAsRead(Integer notificationId, Integer userId) {
        Log notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        // Kiểm tra quyền sở hữu
        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("Notification does not belong to user");
        }
        
        notification.setIsRead(true);
        notification.setReadAt(Instant.now());
        notification = notificationRepository.save(notification);
        
        // Cập nhật thông báo đã đọc trên Firestore
        try {
            firestoreService.markAsRead(userId, notificationId);
        } catch (Exception e) {
            log.error("[Firestore] Error marking as read in Firestore: {}", e.getMessage());
        }
        
        return toResponse(notification);
    }

    // Đánh dấu tất cả thông báo là đã đọc cho user
    @Transactional
    public void markAllAsRead(Integer userId) {
        notificationRepository.markAllAsRead(userId);
    }

    // Đếm số lượng thông báo chưa đọc cho user
    @Transactional(readOnly = true)
    public long countUnread(Integer userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    // Thống kê các loại thông báo của user
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

    // Đồng bộ các thông báo mới kể từ lần sync gần nhất
    @Transactional(readOnly = true)
    public List<NotificationResponse> syncNotifications(Integer userId, Instant lastSyncTime) {
        List<Log> notifications = notificationRepository.findNewNotificationsSince(userId, lastSyncTime);
        return notifications.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Chuyển đổi thực thể Log sang DTO response
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
