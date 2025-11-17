package sunshine_dental_care.dto.hrDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// DTO for attendance explanation request from employee
public class AttendanceExplanationRequest {

    @NotNull(message = "Attendance ID is required")
    private Integer attendanceId;

    // Loại giải trình: LATE, ABSENT, MISSING_CHECK_IN, MISSING_CHECK_OUT
    @NotBlank(message = "Explanation type is required")
    private String explanationType;

    // Lý do giải trình từ nhân viên
    @NotBlank(message = "Reason is required")
    private String reason;

    // Getter cho attendanceId
    public Integer getAttendanceId() {
        return attendanceId;
    }

    // Setter cho attendanceId
    public void setAttendanceId(Integer attendanceId) {
        this.attendanceId = attendanceId;
    }

    // Getter cho explanationType
    public String getExplanationType() {
        return explanationType;
    }

    // Setter cho explanationType
    public void setExplanationType(String explanationType) {
        this.explanationType = explanationType;
    }

    // Getter cho reason
    public String getReason() {
        return reason;
    }

    // Setter cho reason
    public void setReason(String reason) {
        this.reason = reason;
    }
}
