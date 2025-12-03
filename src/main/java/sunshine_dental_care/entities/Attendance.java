package sunshine_dental_care.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@Entity
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendanceId", nullable = false)
    private Integer id;

    @Column(name = "userId", nullable = false)
    private Integer userId;

    @Column(name = "clinicId", nullable = false)
    private Integer clinicId;

    @Column(name = "workDate", nullable = false)
    private LocalDate workDate;

    @Column(name = "checkInTime")
    private Instant checkInTime;

    @Column(name = "checkOutTime")
    private Instant checkOutTime;

    @Nationalized
    @Column(name = "checkInMethod", length = 50)
    private String checkInMethod;

    @ColumnDefault("0")
    @Column(name = "isOvertime", nullable = false)
    private Boolean isOvertime = false;

    @Nationalized
    @Column(name = "note", length = 400)
    private String note;

    @Nationalized
    @Column(name = "shiftType", length = 20)
    private String shiftType;

    @Column(name = "actualWorkHours", precision = 5, scale = 2)
    private BigDecimal actualWorkHours;

    @Column(name = "expectedWorkHours", precision = 5, scale = 2)
    private BigDecimal expectedWorkHours;

    @Column(name = "lateMinutes")
    private Integer lateMinutes;

    @Column(name = "earlyMinutes")
    private Integer earlyMinutes;

    @Column(name = "lunchBreakMinutes")
    private Integer lunchBreakMinutes;

    @Column(name = "faceMatchScore", precision = 5, scale = 4)
    private BigDecimal faceMatchScore;

    @Nationalized
    @Column(name = "verificationStatus", length = 20)
    private String verificationStatus;

    @Nationalized
    @Column(name = "attendanceStatus", length = 20)
    private String attendanceStatus;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "createdAt", nullable = false)
    private Instant createdAt;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "updatedAt", nullable = false)
    private Instant updatedAt;

    // Tự động set createdAt và updatedAt khi tạo bản ghi mới
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    // Tự động cập nhật updatedAt khi cập nhật bản ghi
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Lấy id chấm công
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

    public Integer getClinicId() {
        return clinicId;
    }

    public void setClinicId(Integer clinicId) {
        this.clinicId = clinicId;
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

    // Kiểm tra có phải ca tăng ca không
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

    // Lấy điểm nhận diện khuôn mặt
    public BigDecimal getFaceMatchScore() {
        return faceMatchScore;
    }

    public void setFaceMatchScore(BigDecimal faceMatchScore) {
        this.faceMatchScore = faceMatchScore;
    }

    // Lấy trạng thái xác minh khuôn mặt
    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    // Lấy trạng thái chấm công
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

    // Lấy số giờ làm việc thực tế
    public BigDecimal getActualWorkHours() {
        return actualWorkHours;
    }

    public void setActualWorkHours(BigDecimal actualWorkHours) {
        this.actualWorkHours = actualWorkHours;
    }

    // Lấy số giờ làm việc theo lịch
    public BigDecimal getExpectedWorkHours() {
        return expectedWorkHours;
    }

    public void setExpectedWorkHours(BigDecimal expectedWorkHours) {
        this.expectedWorkHours = expectedWorkHours;
    }

    // Lấy số phút đi trễ
    public Integer getLateMinutes() {
        return lateMinutes;
    }

    public void setLateMinutes(Integer lateMinutes) {
        this.lateMinutes = lateMinutes;
    }

    // Lấy số phút về sớm
    public Integer getEarlyMinutes() {
        return earlyMinutes;
    }

    public void setEarlyMinutes(Integer earlyMinutes) {
        this.earlyMinutes = earlyMinutes;
    }

    // Lấy số phút nghỉ trưa
    public Integer getLunchBreakMinutes() {
        return lunchBreakMinutes;
    }

    public void setLunchBreakMinutes(Integer lunchBreakMinutes) {
        this.lunchBreakMinutes = lunchBreakMinutes;
    }
}