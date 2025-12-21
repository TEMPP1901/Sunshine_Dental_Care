package sunshine_dental_care.services.impl.notification;

import org.springframework.stereotype.Service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FCMService {

    @org.springframework.scheduling.annotation.Async
    public void sendNotification(String token, String title, String body, String actionUrl,
                                 String relatedEntityType, Integer relatedEntityId) {
        log.info("FCMService: Begin sending notification (Async)");
        log.info("Title: {}", title);
        log.info("Body: {}", body);
        log.info("Token: {}...", token != null && token.length() > 30 ? token.substring(0, 30) : token);
        log.info("ActionUrl: {}", actionUrl);

        try {
            // Kiểm tra khởi tạo FirebaseMessaging instance
            if (FirebaseMessaging.getInstance() == null) {
                log.error("FCMService: FirebaseMessaging instance is null!");
                throw new IllegalStateException("FirebaseMessaging not initialized");
            }

            Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            Message.Builder messageBuilder = Message.builder()
                    .setToken(token)
                    .setNotification(notification);

            if (actionUrl != null) {
                messageBuilder.putData("actionUrl", actionUrl);
            }

            if (relatedEntityType != null) {
                messageBuilder.putData("relatedEntityType", relatedEntityType);
            }

            if (relatedEntityId != null) {
                messageBuilder.putData("relatedEntityId", relatedEntityId.toString());
            }

            Message message = messageBuilder.build();
            log.info("FCMService: Sending message to Firebase Cloud Messaging...");

            // Gửi FCM async (không block)
            FirebaseMessaging.getInstance().sendAsync(message);
            log.info("FCMService: FCM message sent request initiated.");
        } catch (Exception e) {
            log.error("FCMService: Error while sending FCM message", e);
            log.error("Error type: {}", e.getClass().getName());
            log.error("Error message: {}", e.getMessage());
            if (e.getCause() != null) {
                log.error("Cause: {}", e.getCause().getMessage());
            }
            // Không throw để tránh rollback transaction của thread chính
        }
    }
}
