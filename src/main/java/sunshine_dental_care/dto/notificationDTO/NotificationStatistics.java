package sunshine_dental_care.dto.notificationDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationStatistics {
    private Long totalNotifications;
    private Long unreadCount;
    private Long readCount;
    private Long highPriorityCount;
    private Long mediumPriorityCount;
    private Long lowPriorityCount;
    private Long expiredCount;
    private Long todayCount;
    private Long thisWeekCount;
    private Long thisMonthCount;
}

