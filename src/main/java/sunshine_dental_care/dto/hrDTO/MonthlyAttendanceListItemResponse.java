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
    private Double presentDays;       // Số ngày present (bác sĩ có thể là 0.5 nếu chỉ làm 1/2 ca)
    private Double lateDays;          // Số ngày late (bác sĩ có thể là 0.5 nếu chỉ làm 1/2 ca)
    private Integer absentDays;       // Số ngày absent
    private Double leaveDays;         // Số ngày nghỉ phép (bác sĩ có thể là 0.5 nếu chỉ nghỉ 1/2 ca)
    private Integer offDays;          // Số ngày nghỉ
    
    // Tổng thời gian làm việc trong tháng
    private Long totalWorkedHours;     // Tổng giờ làm việc
    private Long totalWorkedMinutes;  // Tổng phút làm việc
    private String totalWorkedDisplay; // "176 hr 30 min"
    
    // Thông tin cho tính lương theo giờ
    private Integer totalLateMinutes;   // Tổng số phút đi trễ trong tháng
    private Integer totalEarlyMinutes; // Tổng số phút ra sớm trong tháng
    private Double actualWorkedDays;   // Số ngày đi làm thực tế (bác sĩ có thể là 0.5 nếu chỉ làm 1/2 ca)
    
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
    
    public Double getPresentDays() {
        return presentDays;
    }
    
    public void setPresentDays(Double presentDays) {
        this.presentDays = presentDays;
    }
    
    public Double getLateDays() {
        return lateDays;
    }
    
    public void setLateDays(Double lateDays) {
        this.lateDays = lateDays;
    }
    
    public Integer getAbsentDays() {
        return absentDays;
    }
    
    public void setAbsentDays(Integer absentDays) {
        this.absentDays = absentDays;
    }
    
    public Double getLeaveDays() {
        return leaveDays;
    }
    
    public void setLeaveDays(Double leaveDays) {
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
    
    public Double getActualWorkedDays() {
        return actualWorkedDays;
    }
    
    public void setActualWorkedDays(Double actualWorkedDays) {
        this.actualWorkedDays = actualWorkedDays;
    }
}

