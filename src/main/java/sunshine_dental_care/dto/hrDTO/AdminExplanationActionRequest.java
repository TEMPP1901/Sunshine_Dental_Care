package sunshine_dental_care.dto.hrDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO cho request admin approve/reject giải trình
 */
public class AdminExplanationActionRequest {

    @NotNull(message = "Attendance ID is required")
    private Integer attendanceId;

    /**
     * Action: APPROVE hoặc REJECT
     */
    @NotBlank(message = "Action is required (APPROVE or REJECT)")
    private String action;

    /**
     * Ghi chú từ admin
     */
    private String adminNote;

    // Getters and Setters
    public Integer getAttendanceId() {
        return attendanceId;
    }

    public void setAttendanceId(Integer attendanceId) {
        this.attendanceId = attendanceId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }

    /**
     * Giờ về thực tế (tùy chọn) khi duyệt MISSING_CHECK_OUT
     * Format: HH:mm
     */
    private String customTime;

    public String getCustomTime() {
        return customTime;
    }

    public void setCustomTime(String customTime) {
        this.customTime = customTime;
    }
}
