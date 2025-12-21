package sunshine_dental_care.services.impl.notification;

import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.notificationDTO.NotificationResponse;
import sunshine_dental_care.entities.UserDevice;
import sunshine_dental_care.repositories.UserDeviceRepo;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationAsyncService {

    private final UserDeviceRepo userDeviceRepo;
    private final FCMService fcmService;
    private final SimpMessagingTemplate messagingTemplate;
    private final FirestoreService firestoreService;

    // Phân phối notification tới WebSocket, Firestore, FCM (chạy ngầm)
    @Async
    public void distributeNotification(NotificationResponse notification, String userIdStr) {
        log.info("Starting async distribution for notification {} to user {}", notification.getNotificationId(), userIdStr);

        sendToWebSocket(notification, userIdStr);
        saveToFirestore(notification);
        sendToFCM(notification, Integer.parseInt(userIdStr));

        log.info("Completed async distribution for notification {} to user {}", notification.getNotificationId(), userIdStr);
    }

    // Gửi notification qua WebSocket
    private void sendToWebSocket(NotificationResponse notification, String userId) {
        try {
            // Spring WebSocket convertAndSendToUser sử dụng username từ principal
            // Trong CurrentUser, username được set là String.valueOf(userId)
            // Nên cần đảm bảo userId được truyền đúng format (String)
            String username = userId; // Username trong principal là String.valueOf(userId)
            String destination = "/user/" + username + "/queue/notifications";
            
            log.debug("[WebSocket] Attempting to send notification {} to user {} (username: {}) at destination {}", 
                    notification.getNotificationId(), userId, username, destination);
            
            messagingTemplate.convertAndSendToUser(
                    username,
                    "/queue/notifications",
                    notification);
            
            log.info("[WebSocket] Notification {} sent successfully to user {} (username: {}) at destination {}", 
                    notification.getNotificationId(), userId, username, destination);
        } catch (Exception e) {
            log.error("[WebSocket] Error sending WebSocket message to user {}: {}", userId, e.getMessage(), e);
            // Không throw exception để không làm gián đoạn việc gửi Firestore và FCM
        }
    }

    // Lưu notification lên Firestore
    private void saveToFirestore(NotificationResponse notification) {
        try {
            firestoreService.saveNotification(notification);
            log.info("[Firestore] Notification saved to Firestore for user: {}", notification.getUserId());
        } catch (Exception e) {
            log.error("[Firestore] Error saving to Firestore: {}", e.getMessage(), e);
        }
    }

    // Gửi notification tới tất cả thiết bị FCM của user
    private void sendToFCM(NotificationResponse notification, Integer userId) {
        log.info("Start FCM notification process for user: {}", userId);
        try {
            List<UserDevice> devices = userDeviceRepo.findByUserId(userId);
            log.info("Found {} device(s) for user: {}", devices.size(), userId);

            if (devices.isEmpty()) {
                log.warn("No FCM devices found for user: {}", userId);
            } else {
                int successCount = 0;
                for (UserDevice device : devices) {
                    try {
                        fcmService.sendNotification(
                                device.getFcmToken(),
                                notification.getTitle(),
                                notification.getMessage(),
                                notification.getActionUrl(),
                                notification.getRelatedEntityType(),
                                notification.getRelatedEntityId());
                        successCount++;
                    } catch (Exception e) {
                        log.error("Failed to send FCM to device {} (type: {}): {}", device.getId(), device.getDeviceType(), e.getMessage(), e);
                    }
                }
                log.info("FCM notifications summary: {}/{} devices sent successfully for user: {}", successCount, devices.size(), userId);
            }
        } catch (Exception e) {
            log.error("Error sending FCM notifications: {}", e.getMessage(), e);
        }
    }
}
