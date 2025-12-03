package sunshine_dental_care.dto.hrDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class AttendanceResponse {

    private Integer id;
    private Integer userId;
    private String userName;
    private String userAvatarUrl;
    private String faceImageUrl;
    private Integer clinicId;
    private String clinicName;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate workDate;
    private Instant checkInTime;
    private Instant checkOutTime;
    private String checkInMethod;
    private Boolean isOvertime;
    private String note;

    private BigDecimal faceMatchScore;
    private String verificationStatus;

    private String attendanceStatus;

    private String shiftType;

    private BigDecimal actualWorkHours;
    private BigDecimal expectedWorkHours;

    private Integer lateMinutes;
    private Integer earlyMinutes;
    private Integer lunchBreakMinutes;

    private Boolean wifiValid;
    private String wifiValidationMessage;

    private Instant createdAt;
    private Instant updatedAt;

    // Lấy id của bản ghi chấm công
    public Integer getId() {
        return id;
    }

    // Thiết lập id cho bản ghi chấm công
    public void setId(Integer id) {
        this.id = id;
    }

    // Lấy id người dùng (nhân viên)
    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    // Lấy tên người dùng
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

    // Lấy thời gian chấm công vào
    public Instant getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(Instant checkInTime) {
        this.checkInTime = checkInTime;
    }

    // Lấy thời gian chấm công ra
    public Instant getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(Instant checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    // Lấy phương thức chấm công (face, wifi, gps)
    public String getCheckInMethod() {
        return checkInMethod;
    }

    public void setCheckInMethod(String checkInMethod) {
        this.checkInMethod = checkInMethod;
    }

    // Kiểm tra có làm ngoài giờ không
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

    // Lấy điểm nhận diện khuôn mặt dùng để xác thực nhân viên
    public BigDecimal getFaceMatchScore() {
        return faceMatchScore;
    }

    public void setFaceMatchScore(BigDecimal faceMatchScore) {
        this.faceMatchScore = faceMatchScore;
    }

    // Lấy trạng thái xác thực khuôn mặt (PENDING, VERIFIED, FAILED)
    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    // Lấy trạng thái chấm công (ON_TIME, LATE, ABSENT)
    public String getAttendanceStatus() {
        return attendanceStatus;
    }

    public void setAttendanceStatus(String attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }

    // Loại ca làm việc (MORNING, AFTERNOON, FULL_DAY)
    public String getShiftType() {
        return shiftType;
    }

    public void setShiftType(String shiftType) {
        this.shiftType = shiftType;
    }

    // Tính giờ làm thực tế sau khi trừ trễ và ra sớm
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

    // Tổng số phút đi trễ của nhân viên
    public Integer getLateMinutes() {
        return lateMinutes;
    }

    public void setLateMinutes(Integer lateMinutes) {
        this.lateMinutes = lateMinutes;
    }

    // Tổng số phút ra sớm của nhân viên
    public Integer getEarlyMinutes() {
        return earlyMinutes;
    }

    public void setEarlyMinutes(Integer earlyMinutes) {
        this.earlyMinutes = earlyMinutes;
    }

    // Tổng phút nghỉ trưa
    public Integer getLunchBreakMinutes() {
        return lunchBreakMinutes;
    }

    public void setLunchBreakMinutes(Integer lunchBreakMinutes) {
        this.lunchBreakMinutes = lunchBreakMinutes;
    }

    // Xác thực kết nối wifi tại điểm chấm công
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

    // Thời điểm tạo bản ghi chấm công
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    // Thời điểm cập nhật bản ghi chấm công
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Thời gian bắt đầu ca làm việc (theo lịch)
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm:ss")
    private java.time.LocalTime startTime;

    // Thời gian kết thúc ca làm việc (theo lịch)
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm:ss")
    private java.time.LocalTime endTime;

    public java.time.LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(java.time.LocalTime startTime) {
        this.startTime = startTime;
    }

    public java.time.LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(java.time.LocalTime endTime) {
        this.endTime = endTime;
    }
}
