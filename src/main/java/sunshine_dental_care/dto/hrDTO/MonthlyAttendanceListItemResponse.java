package sunshine_dental_care.dto.hrDTO;

/**
 * DTO cho Monthly Attendance List - Chi tiết từng nhân viên trong tháng
 */
public class MonthlyAttendanceListItemResponse {
    
    private Integer userId;
    private String employeeName;
    private String jobTitle;
    private String avatarUrl;
    
    // Thống kê trong tháng
    private Integer workingDays;      // Số ngày có schedule
    private Integer presentDays;      // Số ngày present (ON_TIME)
    private Integer lateDays;         // Số ngày late
    private Integer absentDays;       // Số ngày absent
    private Integer leaveDays;        // Số ngày nghỉ phép
    private Integer offDays;          // Số ngày nghỉ
    
    // Tổng thời gian làm việc trong tháng
    private Long totalWorkedHours;     // Tổng giờ làm việc
    private Long totalWorkedMinutes;  // Tổng phút làm việc
    private String totalWorkedDisplay; // "176 hr 30 min"
    
    // Thông tin cho tính lương theo giờ
    private Integer totalLateMinutes;   // Tổng số phút đi trễ trong tháng
    private Integer totalEarlyMinutes; // Tổng số phút ra sớm trong tháng
    private Integer actualWorkedDays;  // Số ngày đi làm thực tế (có check-in)
    
    // Getters and Setters
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
    
    public Integer getWorkingDays() {
        return workingDays;
    }
    
    public void setWorkingDays(Integer workingDays) {
        this.workingDays = workingDays;
    }
    
    public Integer getPresentDays() {
        return presentDays;
    }
    
    public void setPresentDays(Integer presentDays) {
        this.presentDays = presentDays;
    }
    
    public Integer getLateDays() {
        return lateDays;
    }
    
    public void setLateDays(Integer lateDays) {
        this.lateDays = lateDays;
    }
    
    public Integer getAbsentDays() {
        return absentDays;
    }
    
    public void setAbsentDays(Integer absentDays) {
        this.absentDays = absentDays;
    }
    
    public Integer getLeaveDays() {
        return leaveDays;
    }
    
    public void setLeaveDays(Integer leaveDays) {
        this.leaveDays = leaveDays;
    }
    
    public Integer getOffDays() {
        return offDays;
    }
    
    public void setOffDays(Integer offDays) {
        this.offDays = offDays;
    }
    
    public Long getTotalWorkedHours() {
        return totalWorkedHours;
    }
    
    public void setTotalWorkedHours(Long totalWorkedHours) {
        this.totalWorkedHours = totalWorkedHours;
    }
    
    public Long getTotalWorkedMinutes() {
        return totalWorkedMinutes;
    }
    
    public void setTotalWorkedMinutes(Long totalWorkedMinutes) {
        this.totalWorkedMinutes = totalWorkedMinutes;
    }
    
    public String getTotalWorkedDisplay() {
        return totalWorkedDisplay;
    }
    
    public void setTotalWorkedDisplay(String totalWorkedDisplay) {
        this.totalWorkedDisplay = totalWorkedDisplay;
    }
    
    public Integer getTotalLateMinutes() {
        return totalLateMinutes;
    }
    
    public void setTotalLateMinutes(Integer totalLateMinutes) {
        this.totalLateMinutes = totalLateMinutes;
    }
    
    public Integer getTotalEarlyMinutes() {
        return totalEarlyMinutes;
    }
    
    public void setTotalEarlyMinutes(Integer totalEarlyMinutes) {
        this.totalEarlyMinutes = totalEarlyMinutes;
    }
    
    public Integer getActualWorkedDays() {
        return actualWorkedDays;
    }
    
    public void setActualWorkedDays(Integer actualWorkedDays) {
        this.actualWorkedDays = actualWorkedDays;
    }
}

