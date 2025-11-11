package sunshine_dental_care.dto.hrDTO;

/**
 * DTO cho Monthly Summary List - Tổng hợp attendance theo department trong tháng
 */
public class MonthlySummaryResponse {
    
    private Integer departmentId;
    private String departmentName;
    
    // Tổng số nhân viên trong department
    private Integer totalEmployees;
    
    // Số ngày làm việc trong tháng
    private Integer workingDays;
    
    // Trạng thái attendance (tổng hợp cả tháng)
    private Integer present;      // Tổng số lần ON_TIME
    private Integer late;         // Tổng số lần LATE
    private Integer absent;       // Tổng số lần ABSENT
    private Integer leave;        // Tổng số lần nghỉ phép
    private Integer offday;       // Tổng số ngày nghỉ
    
    // Tổng số attendance records
    private Integer totalAttendance;
    
    // Getters and Setters
    public Integer getDepartmentId() {
        return departmentId;
    }
    
    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }
    
    public String getDepartmentName() {
        return departmentName;
    }
    
    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }
    
    public Integer getTotalEmployees() {
        return totalEmployees;
    }
    
    public void setTotalEmployees(Integer totalEmployees) {
        this.totalEmployees = totalEmployees;
    }
    
    public Integer getWorkingDays() {
        return workingDays;
    }
    
    public void setWorkingDays(Integer workingDays) {
        this.workingDays = workingDays;
    }
    
    public Integer getPresent() {
        return present;
    }
    
    public void setPresent(Integer present) {
        this.present = present;
    }
    
    public Integer getLate() {
        return late;
    }
    
    public void setLate(Integer late) {
        this.late = late;
    }
    
    public Integer getAbsent() {
        return absent;
    }
    
    public void setAbsent(Integer absent) {
        this.absent = absent;
    }
    
    public Integer getLeave() {
        return leave;
    }
    
    public void setLeave(Integer leave) {
        this.leave = leave;
    }
    
    public Integer getOffday() {
        return offday;
    }
    
    public void setOffday(Integer offday) {
        this.offday = offday;
    }
    
    public Integer getTotalAttendance() {
        return totalAttendance;
    }
    
    public void setTotalAttendance(Integer totalAttendance) {
        this.totalAttendance = totalAttendance;
    }
}

