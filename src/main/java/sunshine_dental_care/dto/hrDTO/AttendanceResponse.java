package sunshine_dental_care.dto.hrDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO cho response của Attendance
 */
public class AttendanceResponse {
    
    private Integer id;
    private Integer userId;
    private String userName;
    private String userAvatarUrl;
    private String faceImageUrl;  // Face image từ EmployeeFaceProfile
    private Integer clinicId;
    private String clinicName;
    private LocalDate workDate;
    private Instant checkInTime;
    private Instant checkOutTime;
    private String checkInMethod;
    private Boolean isOvertime;
    private String note;
    
    // Face verification results
    private BigDecimal faceMatchScore;
    private String verificationStatus;  // PENDING, VERIFIED, FAILED
    
    // Attendance status
    private String attendanceStatus;  // ON_TIME, LATE, ABSENT
    
    // Shift type (for doctors: MORNING, AFTERNOON, FULL_DAY)
    private String shiftType;  // MORNING, AFTERNOON, FULL_DAY
    
    // Work hours
    private BigDecimal actualWorkHours;  // Số giờ làm việc thực tế (đã trừ đi trễ, ra sớm, nghỉ trưa)
    private BigDecimal expectedWorkHours;  // Số giờ làm việc theo lịch
    
    // Time tracking details
    private Integer lateMinutes;  // Số phút đi trễ
    private Integer earlyMinutes;  // Số phút ra sớm
    private Integer lunchBreakMinutes;  // Số phút nghỉ trưa
    
    // WiFi validation results
    private Boolean wifiValid;
    private String wifiValidationMessage;
    
    private Instant createdAt;
    private Instant updatedAt;
    
    // Getters and Setters
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
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
    
    public String getUserAvatarUrl() {
        return userAvatarUrl;
    }
    
    public void setUserAvatarUrl(String userAvatarUrl) {
        this.userAvatarUrl = userAvatarUrl;
    }
    
    public String getFaceImageUrl() {
        return faceImageUrl;
    }
    
    public void setFaceImageUrl(String faceImageUrl) {
        this.faceImageUrl = faceImageUrl;
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
    
    public String getCheckInMethod() {
        return checkInMethod;
    }
    
    public void setCheckInMethod(String checkInMethod) {
        this.checkInMethod = checkInMethod;
    }

    public Boolean getIsOvertime() {
        return isOvertime;
    }
    
    public void setIsOvertime(Boolean isOvertime) {
        this.isOvertime = isOvertime;
    }
    
    public String getNote() {
        return note;
    }
    
    public void setNote(String note) {
        this.note = note;
    }
    
    public BigDecimal getFaceMatchScore() {
        return faceMatchScore;
    }
    
    public void setFaceMatchScore(BigDecimal faceMatchScore) {
        this.faceMatchScore = faceMatchScore;
    }
    
    public String getVerificationStatus() {
        return verificationStatus;
    }
    
    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }
    
    public String getAttendanceStatus() {
        return attendanceStatus;
    }
    
    public void setAttendanceStatus(String attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }
    
    public String getShiftType() {
        return shiftType;
    }
    
    public void setShiftType(String shiftType) {
        this.shiftType = shiftType;
    }
    
    public BigDecimal getActualWorkHours() {
        return actualWorkHours;
    }
    
    public void setActualWorkHours(BigDecimal actualWorkHours) {
        this.actualWorkHours = actualWorkHours;
    }
    
    public BigDecimal getExpectedWorkHours() {
        return expectedWorkHours;
    }
    
    public void setExpectedWorkHours(BigDecimal expectedWorkHours) {
        this.expectedWorkHours = expectedWorkHours;
    }
    
    public Integer getLateMinutes() {
        return lateMinutes;
    }
    
    public void setLateMinutes(Integer lateMinutes) {
        this.lateMinutes = lateMinutes;
    }
    
    public Integer getEarlyMinutes() {
        return earlyMinutes;
    }
    
    public void setEarlyMinutes(Integer earlyMinutes) {
        this.earlyMinutes = earlyMinutes;
    }
    
    public Integer getLunchBreakMinutes() {
        return lunchBreakMinutes;
    }
    
    public void setLunchBreakMinutes(Integer lunchBreakMinutes) {
        this.lunchBreakMinutes = lunchBreakMinutes;
    }
    
    public Boolean getWifiValid() {
        return wifiValid;
    }
    
    public void setWifiValid(Boolean wifiValid) {
        this.wifiValid = wifiValid;
    }
    
    public String getWifiValidationMessage() {
        return wifiValidationMessage;
    }
    
    public void setWifiValidationMessage(String wifiValidationMessage) {
        this.wifiValidationMessage = wifiValidationMessage;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
}

