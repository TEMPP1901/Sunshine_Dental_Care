package sunshine_dental_care.dto.hrDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO cho request giải trình attendance từ nhân viên
 */
public class AttendanceExplanationRequest {
    
    @NotNull(message = "Attendance ID is required")
    private Integer attendanceId;
    
    /**
     * Loại giải trình: LATE, ABSENT, MISSING_CHECK_IN, MISSING_CHECK_OUT
     */
    @NotBlank(message = "Explanation type is required")
    private String explanationType;
    
    /**
     * Lý do giải trình từ nhân viên
     */
    @NotBlank(message = "Reason is required")
    private String reason;
    
    // Getters and Setters
    public Integer getAttendanceId() {
        return attendanceId;
    }
    
    public void setAttendanceId(Integer attendanceId) {
        this.attendanceId = attendanceId;
    }
    
    public String getExplanationType() {
        return explanationType;
    }
    
    public void setExplanationType(String explanationType) {
        this.explanationType = explanationType;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
}

