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

    /**
     * Loại ca làm việc: MORNING (sáng), AFTERNOON (chiều), FULL_DAY (cả ngày)
     * - MORNING: Ca sáng (8:00-11:00)
     * - AFTERNOON: Ca chiều (13:00-18:00)
     * - FULL_DAY: Cả ngày (cho nhân viên không phân ca)
     */
    @Nationalized
    @Column(name = "shiftType", length = 20)
    private String shiftType; // MORNING, AFTERNOON, FULL_DAY

    /**
     * Số giờ làm việc thực tế (tính từ check-in đến check-out)
     * Dùng để tính lương
     */
    @Column(name = "actualWorkHours", precision = 5, scale = 2)
    private BigDecimal actualWorkHours; // Ví dụ: 3.5 giờ

    /**
     * Số giờ làm việc theo lịch (expected hours)
     * - Ca sáng: 3 giờ (8:00-11:00)
     * - Ca chiều: 5 giờ (13:00-18:00)
     */
    @Column(name = "expectedWorkHours", precision = 5, scale = 2)
    private BigDecimal expectedWorkHours; // Ví dụ: 3.0 giờ

    /**
     * Số phút đi trễ (nếu check-in sau expectedStartTime)
     * Dùng để thống kê và báo cáo
     */
    @Column(name = "lateMinutes")
    private Integer lateMinutes;

    /**
     * Số phút ra sớm (nếu check-out trước expectedEndTime)
     * Dùng để thống kê và báo cáo
     */
    @Column(name = "earlyMinutes")
    private Integer earlyMinutes;

    /**
     * Số phút nghỉ trưa (chỉ cho nhân viên FULL_DAY)
     * Mặc định: 120 phút (2 giờ) nếu check-in trước 11:00 và check-out sau 13:00
     */
    @Column(name = "lunchBreakMinutes")
    private Integer lunchBreakMinutes;

    // Face Recognition fields
    @Column(name = "faceMatchScore", precision = 5, scale = 4)
    private BigDecimal faceMatchScore; // Similarity score 0.0 - 1.0

    @Nationalized
    @Column(name = "verificationStatus", length = 20)
    private String verificationStatus; // PENDING, VERIFIED, FAILED

    /**
     * Trạng thái chấm công: ON_TIME, LATE, ABSENT
     * - ON_TIME: Check-in đúng giờ (trước hoặc đúng startTime)
     * - LATE: Check-in muộn (sau startTime)
     * - ABSENT: Không check-in trong ngày có schedule
     */
    @Nationalized
    @Column(name = "attendanceStatus", length = 20)
    private String attendanceStatus; // ON_TIME, LATE, ABSENT

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "createdAt", nullable = false)
    private Instant createdAt;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "updatedAt", nullable = false)
    private Instant updatedAt;

    /**
     * Tự động set createdAt và updatedAt trước khi persist (insert)
     */
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

    /**
     * Tự động set updatedAt trước khi update
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

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

}