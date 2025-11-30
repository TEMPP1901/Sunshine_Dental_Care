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
public class NotificationResponse {
    private Integer notificationId;
    private Integer userId;
    private String type;
    private String priority;
    private String title;
    private String message;
    private Boolean isRead;
    private Instant readAt;
    private String actionUrl;
    private String relatedEntityType;
    private Integer relatedEntityId;
    private Instant createdAt;
    private Instant expiresAt;
}

