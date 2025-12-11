package sunshine_dental_care.dto.patientDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientDashboardDTO {
    private String fullName;
    private String patientCode;
    private String avatarUrl;

    // --- KHỐI 1: TRẠNG THÁI SỨC KHỎE (WELLNESS) ---
    private String healthStatus;      // "Excellent", "Warning", "Overdue", "New"
    private String healthMessage;     // Thông điệp (VD: "Đến hạn lấy cao răng")
    private long daysSinceLastVisit;  // Số ngày từ lần khám cuối

    // --- KHỐI 2: LỊCH HẸN SẮP TỚI ---
    private UpcomingAppointmentDTO nextAppointment;

    // --- KHỐI 3: THÔNG TIN (AI & HISTORY) ---
    private String latestAiTip;       // Lời khuyên (Dựa trên lịch sử khám)
    private List<ActivityDTO> recentActivities; // 3 hoạt động gần nhất

    @Data
    @Builder
    public static class UpcomingAppointmentDTO {
        private Integer appointmentId;
        private String doctorName;
        private LocalDateTime startDateTime;
        private String serviceName;
        private String status;
        private String roomName;
    }

    @Data
    @Builder
    public static class ActivityDTO {
        private String title;
        private String date; // Format: dd/MM/yyyy
        private String type; // COMPLETED, CANCELLED...
    }
}