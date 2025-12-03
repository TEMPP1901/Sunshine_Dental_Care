package sunshine_dental_care.dto.hrDTO;

import jakarta.validation.constraints.NotBlank;

// DTO for attendance explanation request from employee
public class AttendanceExplanationRequest {

    // Attendance ID - có thể là 0 nếu chưa có attendance record (sẽ tự động tạo)
    private Integer attendanceId;

    // Loại giải trình: LATE, ABSENT, MISSING_CHECK_IN, MISSING_CHECK_OUT
    @NotBlank(message = "Explanation type is required")
    private String explanationType;

    // Lý do giải trình từ nhân viên
    @NotBlank(message = "Reason is required")
    private String reason;

    // ShiftType (MORNING, AFTERNOON) - chỉ cần khi attendanceId = 0 (chưa có attendance record)
    private String shiftType;

    // ClinicId - chỉ cần khi attendanceId = 0 (chưa có attendance record)
    private Integer clinicId;

    // WorkDate - chỉ cần khi attendanceId = 0 (chưa có attendance record)
    private java.time.LocalDate workDate;

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

    // Getter cho shiftType
    public String getShiftType() {
        return shiftType;
    }

    // Setter cho shiftType
    public void setShiftType(String shiftType) {
        this.shiftType = shiftType;
    }

    // Getter cho clinicId
    public Integer getClinicId() {
        return clinicId;
    }

    // Setter cho clinicId
    public void setClinicId(Integer clinicId) {
        this.clinicId = clinicId;
    }

    // Getter cho workDate
    public java.time.LocalDate getWorkDate() {
        return workDate;
    }

    // Setter cho workDate
    public void setWorkDate(java.time.LocalDate workDate) {
        this.workDate = workDate;
    }
}
