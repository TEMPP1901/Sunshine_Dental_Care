package sunshine_dental_care.dto.hrDTO;

import java.time.Instant;
import java.time.LocalDate;

// DTO for attendance explanation response
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

    private String explanationType; // LATE, ABSENT, MISSING_CHECK_IN, MISSING_CHECK_OUT
    private String employeeReason; // employee's explanation reason (from note)
    private String explanationStatus; // PENDING, APPROVED, REJECTED
    private String adminNote; // admin's note (if any)
    private String note; // full note for parsing

    // Getter và setter cho attendanceId
    public Integer getAttendanceId() {
        return attendanceId;
    }

    public void setAttendanceId(Integer attendanceId) {
        this.attendanceId = attendanceId;
    }

    // Getter và setter cho userId
    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    // Getter và setter cho userName
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    // Getter và setter cho clinicId
    public Integer getClinicId() {
        return clinicId;
    }

    public void setClinicId(Integer clinicId) {
        this.clinicId = clinicId;
    }

    // Getter và setter cho clinicName
    public String getClinicName() {
        return clinicName;
    }

    public void setClinicName(String clinicName) {
        this.clinicName = clinicName;
    }

    // Getter và setter cho workDate
    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    // Getter và setter cho checkInTime
    public Instant getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(Instant checkInTime) {
        this.checkInTime = checkInTime;
    }

    // Getter và setter cho checkOutTime
    public Instant getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(Instant checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    // Getter và setter cho attendanceStatus
    public String getAttendanceStatus() {
        return attendanceStatus;
    }

    public void setAttendanceStatus(String attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }

    // Getter và setter cho explanationType
    public String getExplanationType() {
        return explanationType;
    }

    public void setExplanationType(String explanationType) {
        this.explanationType = explanationType;
    }

    // Getter và setter cho employeeReason
    public String getEmployeeReason() {
        return employeeReason;
    }

    public void setEmployeeReason(String employeeReason) {
        this.employeeReason = employeeReason;
    }

    // Getter và setter cho explanationStatus
    public String getExplanationStatus() {
        return explanationStatus;
    }

    public void setExplanationStatus(String explanationStatus) {
        this.explanationStatus = explanationStatus;
    }

    // Getter và setter cho adminNote
    public String getAdminNote() {
        return adminNote;
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }

    // Getter và setter cho note
    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
