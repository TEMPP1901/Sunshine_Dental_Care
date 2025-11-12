package sunshine_dental_care.dto.hrDTO;

import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO cho response giải trình attendance
 */
public class AttendanceExplanationResponse {
    
    private Integer attendanceId;
    private Integer userId;
    private String userName;
    private Integer clinicId;
    private String clinicName;
    private LocalDate workDate;
    private Instant checkInTime;
    private Instant checkOutTime;
    private String attendanceStatus; // LATE, ABSENT, etc.
    
    /**
     * Loại giải trình: LATE, ABSENT, MISSING_CHECK_IN, MISSING_CHECK_OUT
     */
    private String explanationType;
    
    /**
     * Lý do giải trình từ nhân viên (từ note)
     */
    private String employeeReason;
    
    /**
     * Trạng thái giải trình: PENDING, APPROVED, REJECTED
     */
    private String explanationStatus;
    
    /**
     * Ghi chú từ admin (nếu có)
     */
    private String adminNote;
    
    /**
     * Toàn bộ note (để parse)
     */
    private String note;
    
    // Getters and Setters
    public Integer getAttendanceId() {
        return attendanceId;
    }
    
    public void setAttendanceId(Integer attendanceId) {
        this.attendanceId = attendanceId;
    }
    
    public Integer getUserId() {
        return userId;
    }
    
    public void setUserId(Integer userId) {
        this.userId = userId;
    }
    
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public Integer getClinicId() {
        return clinicId;
    }
    
    public void setClinicId(Integer clinicId) {
        this.clinicId = clinicId;
    }
    
    public String getClinicName() {
        return clinicName;
    }
    
    public void setClinicName(String clinicName) {
        this.clinicName = clinicName;
    }
    
    public LocalDate getWorkDate() {
        return workDate;
    }
    
    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }
    
    public Instant getCheckInTime() {
        return checkInTime;
    }
    
    public void setCheckInTime(Instant checkInTime) {
        this.checkInTime = checkInTime;
    }
    
    public Instant getCheckOutTime() {
        return checkOutTime;
    }
    
    public void setCheckOutTime(Instant checkOutTime) {
        this.checkOutTime = checkOutTime;
    }
    
    public String getAttendanceStatus() {
        return attendanceStatus;
    }
    
    public void setAttendanceStatus(String attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }
    
    public String getExplanationType() {
        return explanationType;
    }
    
    public void setExplanationType(String explanationType) {
        this.explanationType = explanationType;
    }
    
    public String getEmployeeReason() {
        return employeeReason;
    }
    
    public void setEmployeeReason(String employeeReason) {
        this.employeeReason = employeeReason;
    }
    
    public String getExplanationStatus() {
        return explanationStatus;
    }
    
    public void setExplanationStatus(String explanationStatus) {
        this.explanationStatus = explanationStatus;
    }
    
    public String getAdminNote() {
        return adminNote;
    }
    
    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }
    
    public String getNote() {
        return note;
    }
    
    public void setNote(String note) {
        this.note = note;
    }
}

