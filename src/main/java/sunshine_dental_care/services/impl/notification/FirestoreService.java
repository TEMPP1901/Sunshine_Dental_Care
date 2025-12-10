package sunshine_dental_care.services.impl.notification;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;

import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.notificationDTO.NotificationResponse;

@Service
@Slf4j
public class FirestoreService {

    // Lấy Firestore instance
    private Firestore getFirestore() {
        try {
            return FirestoreClient.getFirestore();
        } catch (Exception e) {
            log.error("[Firestore] Failed to get Firestore instance: {}", e.getMessage());
            return null;
        }
    }

    @org.springframework.scheduling.annotation.Async
    public void saveNotification(NotificationResponse notification) {
        try {
            Firestore db = getFirestore();
            if (db == null) {
                log.warn("[Firestore] Firestore instance is null, skipping save");
                return;
            }

            String userId = String.valueOf(notification.getUserId());
            String notificationId = String.valueOf(notification.getNotificationId());
            String collectionPath = "notifications/" + userId + "/items";

            // Chuẩn bị dữ liệu notification
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("notificationId", notification.getNotificationId());
            notificationData.put("userId", notification.getUserId());
            notificationData.put("type", notification.getType());
            notificationData.put("priority", notification.getPriority());
            notificationData.put("title", notification.getTitle());
            notificationData.put("message", notification.getMessage());
            notificationData.put("actionUrl", notification.getActionUrl() != null ? notification.getActionUrl() : "");
            notificationData.put("relatedEntityType",
                    notification.getRelatedEntityType() != null ? notification.getRelatedEntityType() : "");
            notificationData.put("relatedEntityId",
                    notification.getRelatedEntityId() != null ? notification.getRelatedEntityId() : "");
            notificationData.put("isRead", notification.getIsRead());
            notificationData.put("createdAt",
                    notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : Instant.now().toString());
            notificationData.put("expiresAt",
                    notification.getExpiresAt() != null ? notification.getExpiresAt().toString() : "");
            notificationData.put("readAt", notification.getReadAt() != null ? notification.getReadAt().toString() : "");

            // Lưu notification, không block thread chính
            db.collection(collectionPath)
                .document(notificationId)
                .set(notificationData);

            log.info("[Firestore] Notification save initiated (Async) - UserId: {}, NotificationId: {}", userId, notificationId);
        } catch (Exception e) {
            log.error("[Firestore] Error initiating save to Firestore: {}", e.getMessage(), e);
        }
    }

    public void markAsRead(Integer userId, Integer notificationId) {
        try {
            Firestore db = getFirestore();
            if (db == null) {
                log.warn("[Firestore] Firestore instance is null, skipping mark as read");
                return;
            }

            String collectionPath = "notifications/" + userId + "/items";
            String documentId = String.valueOf(notificationId);
            var docRef = db.collection(collectionPath).document(documentId);
            var docSnapshot = docRef.get().get();

            // Nếu không tồn tại thì bỏ qua
            if (!docSnapshot.exists()) {
                log.warn("[Firestore] Document not found, skipping mark as read - UserId: {}, NotificationId: {}", userId, notificationId);
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("isRead", true);
            updates.put("readAt", Instant.now().toString());

            docRef.update(updates).get();

            log.info("[Firestore] Notification marked as read - UserId: {}, NotificationId: {}", userId, notificationId);
        } catch (com.google.api.gax.rpc.NotFoundException e) {
            // Có thể không tồn tại vì tạo notification trước khi tích hợp Firestore
            log.warn("[Firestore] Document not found when marking as read - UserId: {}, NotificationId: {}. This is normal if notification was created before Firestore integration.",
                    userId, notificationId);
        } catch (Exception e) {
            log.error("[Firestore] Error marking notification as read: {}", e.getMessage(), e);
        }
    }

    public void deleteNotification(Integer userId, Integer notificationId) {
        try {
            Firestore db = getFirestore();
            if (db == null) {
                log.warn("[Firestore] Firestore instance is null, skipping delete");
                return;
            }

            String collectionPath = "notifications/" + userId + "/items";
            db.collection(collectionPath)
                .document(String.valueOf(notificationId))
                .delete()
                .get();

            log.info("[Firestore] Notification deleted - UserId: {}, NotificationId: {}", userId, notificationId);
        } catch (Exception e) {
            log.error("[Firestore] Error deleting notification: {}", e.getMessage(), e);
        }
    }
}
