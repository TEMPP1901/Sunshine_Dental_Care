package sunshine_dental_care.dto.hrDTO;

import java.time.Instant;
import java.time.LocalTime;

/**
 * DTO cho Daily Attendance List - Chi tiết từng nhân viên
 */
public class DailyAttendanceListItemResponse {
    
    private Integer id;                    // Attendance ID
    private Integer userId;
    private String employeeName;
    private String jobTitle;                // Role name
    private String avatarUrl;
    
    private String status;                  // Present, Absent, Late, Overtime, Leave
    private String statusColor;             // Màu cho status pill
    
    private Instant checkInTime;
    private Instant checkOutTime;
    
    private LocalTime shiftStartTime;       // Giờ bắt đầu ca làm việc
    private LocalTime shiftEndTime;         // Giờ kết thúc ca làm việc
    private String shiftDisplay;            // "9 am - 6 pm"
    private String shiftHours;              // "9 hr Shift: A"
    
    private Long workedHours;               // Tổng giờ làm việc (hours)
    private Long workedMinutes;             // Tổng phút làm việc (minutes)
    private String workedDisplay;           // "69 hr 00 min"
    
    private String remarks;                 // Ghi chú
    
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
    
    public String getEmployeeName() {
        return employeeName;
    }
    
    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }
    
    public String getJobTitle() {
        return jobTitle;
    }
    
    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getStatusColor() {
        return statusColor;
    }
    
    public void setStatusColor(String statusColor) {
        this.statusColor = statusColor;
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
    
    public LocalTime getShiftStartTime() {
        return shiftStartTime;
    }
    
    public void setShiftStartTime(LocalTime shiftStartTime) {
        this.shiftStartTime = shiftStartTime;
    }
    
    public LocalTime getShiftEndTime() {
        return shiftEndTime;
    }
    
    public void setShiftEndTime(LocalTime shiftEndTime) {
        this.shiftEndTime = shiftEndTime;
    }
    
    public String getShiftDisplay() {
        return shiftDisplay;
    }
    
    public void setShiftDisplay(String shiftDisplay) {
        this.shiftDisplay = shiftDisplay;
    }
    
    public String getShiftHours() {
        return shiftHours;
    }
    
    public void setShiftHours(String shiftHours) {
        this.shiftHours = shiftHours;
    }
    
    public Long getWorkedHours() {
        return workedHours;
    }
    
    public void setWorkedHours(Long workedHours) {
        this.workedHours = workedHours;
    }
    
    public Long getWorkedMinutes() {
        return workedMinutes;
    }
    
    public void setWorkedMinutes(Long workedMinutes) {
        this.workedMinutes = workedMinutes;
    }
    
    public String getWorkedDisplay() {
        return workedDisplay;
    }
    
    public void setWorkedDisplay(String workedDisplay) {
        this.workedDisplay = workedDisplay;
    }
    
    public String getRemarks() {
        return remarks;
    }
    
    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

}

