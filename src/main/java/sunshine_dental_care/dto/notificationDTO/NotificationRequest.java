package sunshine_dental_care.dto.notificationDTO;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    private Integer userId;
    private String type;
    private String priority;
    private String title;
    private String message;
    private String actionUrl;
    private String relatedEntityType;
    private Integer relatedEntityId;
    private Instant expiresAt; // Optional: notification expiration time
}
