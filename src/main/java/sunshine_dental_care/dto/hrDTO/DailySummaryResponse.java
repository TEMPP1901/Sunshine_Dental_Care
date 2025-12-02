package sunshine_dental_care.dto.hrDTO;

import java.math.BigDecimal;

/**
 * DTO cho Daily Summary List - Tổng hợp attendance theo department
 */
public class DailySummaryResponse {
    
    private Integer departmentId;
    private String departmentName;
    
    // Tổng số nhân viên trong department
    private Integer totalEmployees;
    
    // Giới tính
    private Integer male;
    private Integer female;
    
    // Trạng thái attendance
    private Integer present;      // ON_TIME
    private BigDecimal presentPercent;
    
    private Integer late;
    private BigDecimal latePercent;
    
    private Integer absent;
    private BigDecimal absentPercent;
    
    private Integer leave;        // Nghỉ phép (cần thêm logic)
    private BigDecimal leavePercent;
    
    private Integer offday;       // Ngày nghỉ (không có schedule)
    private BigDecimal offdayPercent;
    
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
    
    public Integer getMale() {
        return male;
    }
    
    public void setMale(Integer male) {
        this.male = male;
    }
    
    public Integer getFemale() {
        return female;
    }
    
    public void setFemale(Integer female) {
        this.female = female;
    }
    
    public Integer getPresent() {
        return present;
    }
    
    public void setPresent(Integer present) {
        this.present = present;
    }
    
    public BigDecimal getPresentPercent() {
        return presentPercent;
    }
    
    public void setPresentPercent(BigDecimal presentPercent) {
        this.presentPercent = presentPercent;
    }
    
    public Integer getLate() {
        return late;
    }
    
    public void setLate(Integer late) {
        this.late = late;
    }
    
    public BigDecimal getLatePercent() {
        return latePercent;
    }
    
    public void setLatePercent(BigDecimal latePercent) {
        this.latePercent = latePercent;
    }
    
    public Integer getAbsent() {
        return absent;
    }
    
    public void setAbsent(Integer absent) {
        this.absent = absent;
    }
    
    public BigDecimal getAbsentPercent() {
        return absentPercent;
    }
    
    public void setAbsentPercent(BigDecimal absentPercent) {
        this.absentPercent = absentPercent;
    }
    
    public Integer getLeave() {
        return leave;
    }
    
    public void setLeave(Integer leave) {
        this.leave = leave;
    }
    
    public BigDecimal getLeavePercent() {
        return leavePercent;
    }
    
    public void setLeavePercent(BigDecimal leavePercent) {
        this.leavePercent = leavePercent;
    }
    
    public Integer getOffday() {
        return offday;
    }
    
    public void setOffday(Integer offday) {
        this.offday = offday;
    }
    
    public BigDecimal getOffdayPercent() {
        return offdayPercent;
    }
    
    public void setOffdayPercent(BigDecimal offdayPercent) {
        this.offdayPercent = offdayPercent;
    }
}

