package sunshine_dental_care.dto.patientDTO;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PatientDashboardDTO {
    private String fullName;
    private String patientCode;
    private String avatarUrl;

    // Membership
    private String memberTier;
    private BigDecimal totalSpent;
    private BigDecimal nextTierGoal;
    private int currentDiscountPct;

    // Wellness
    private String healthStatus;
    private String healthMessage;
    private long daysSinceLastVisit;

    // Appointment
    private UpcomingAppointmentDTO nextAppointment;

    // Medical History (LIST)
    private List<MedicalRecordDTO> medicalHistory;

    // AI & Info
    private String latestAiTip;
    private List<ActivityDTO> recentActivities;

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
        private String date;
        private String type;
    }

    @Data
    @Builder
    public static class MedicalRecordDTO {
        private Integer recordId;
        private String visitDate;
        private String diagnosis;
        private String treatment;
        private String doctorName;
        private String note;
        private String prescriptionNote;
        private String imageUrl;
    }
}