package sunshine_dental_care.dto.hrDTO;

import java.time.LocalDate;

/**
 * DTO cho cả tạo đơn xin nghỉ và duyệt/từ chối đơn
 * - Khi tạo đơn: cần clinicId, startDate, endDate, type, reason
 * - Khi duyệt/từ chối: cần leaveRequestId, action (APPROVE/REJECT), comment (optional)
 */
public class LeaveRequestRequest {
    
    // Dùng cho tạo đơn xin nghỉ
    private Integer clinicId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String type; // VACATION, SICK, PERSONAL, OTHER
    private String reason;
    private String shiftType; // MORNING, AFTERNOON, FULL_DAY (null = full day, only for doctors)
    
    // Dùng cho duyệt/từ chối đơn
    private Integer leaveRequestId;
    private String action; // APPROVE, REJECT
    private String comment; // Optional comment from HR
    
    // Getters and Setters
    public Integer getClinicId() {
        return clinicId;
    }
    
    public void setClinicId(Integer clinicId) {
        this.clinicId = clinicId;
    }
    
    public LocalDate getStartDate() {
        return startDate;
    }
    
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }
    
    public LocalDate getEndDate() {
        return endDate;
    }
    
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public String getShiftType() {
        return shiftType;
    }
    
    public void setShiftType(String shiftType) {
        this.shiftType = shiftType;
    }
    
    public Integer getLeaveRequestId() {
        return leaveRequestId;
    }
    
    public void setLeaveRequestId(Integer leaveRequestId) {
        this.leaveRequestId = leaveRequestId;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getComment() {
        return comment;
    }
    
    public void setComment(String comment) {
        this.comment = comment;
    }
}

