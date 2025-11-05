package sunshine_dental_care.dto.hrDTO;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class CreateWeeklyScheduleRequest {
    @NotNull(message = "Week start is required")
    private LocalDate weekStart;    // Thứ 2 của tuần
    
    @NotEmpty(message = "Daily assignments cannot be empty")
    private Map<String, List<DoctorAssignmentRequest>> dailyAssignments; // Ngày -> Danh sách bác sĩ
    
    private String note;
    
    // Constructors
    public CreateWeeklyScheduleRequest() {}
    
    public CreateWeeklyScheduleRequest(LocalDate weekStart, 
                                     Map<String, List<DoctorAssignmentRequest>> dailyAssignments, String note) {
        this.weekStart = weekStart;
        this.dailyAssignments = dailyAssignments;
        this.note = note;
    }
    
    // Getters and Setters
    public LocalDate getWeekStart() {
        return weekStart;
    }
    
    public void setWeekStart(LocalDate weekStart) {
        this.weekStart = weekStart;
    }
    
    public Map<String, List<DoctorAssignmentRequest>> getDailyAssignments() {
        return dailyAssignments;
    }
    
    public void setDailyAssignments(Map<String, List<DoctorAssignmentRequest>> dailyAssignments) {
        this.dailyAssignments = dailyAssignments;
    }
    
    public String getNote() {
        return note;
    }
    
    public void setNote(String note) {
        this.note = note;
    }
    
    public static class DoctorAssignmentRequest {
        @NotNull(message = "Doctor ID is required")
        private Integer doctorId;
        
        @NotNull(message = "Clinic ID is required")
        private Integer clinicId;
        
        @NotNull(message = "Room ID is required")
        private Integer roomId;
        
        @NotNull(message = "Chair ID is required")
        private Integer chairId;
        
        @NotNull(message = "Start time is required")
        private LocalTime startTime;
        
        @NotNull(message = "End time is required")
        private LocalTime endTime;
        
        private String note;
        
        // Constructors
        public DoctorAssignmentRequest() {}
        
        public DoctorAssignmentRequest(Integer doctorId, Integer clinicId, 
                                     Integer roomId, Integer chairId, 
                                     LocalTime startTime, LocalTime endTime, String note) {
            this.doctorId = doctorId;
            this.clinicId = clinicId;
            this.roomId = roomId;
            this.chairId = chairId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.note = note;
        }
        
        // Getters and Setters
        public Integer getDoctorId() {
            return doctorId;
        }
        
        public void setDoctorId(Integer doctorId) {
            this.doctorId = doctorId;
        }
        
        public Integer getClinicId() {
            return clinicId;
        }
        
        public void setClinicId(Integer clinicId) {
            this.clinicId = clinicId;
        }
        
        public Integer getRoomId() {
            return roomId;
        }
        
        public void setRoomId(Integer roomId) {
            this.roomId = roomId;
        }
        
        public Integer getChairId() {
            return chairId;
        }
        
        public void setChairId(Integer chairId) {
            this.chairId = chairId;
        }
        
        public LocalTime getStartTime() {
            return startTime;
        }
        
        public void setStartTime(LocalTime startTime) {
            this.startTime = startTime;
        }
        
        public LocalTime getEndTime() {
            return endTime;
        }
        
        public void setEndTime(LocalTime endTime) {
            this.endTime = endTime;
        }
        
        public String getNote() {
            return note;
        }
        
        public void setNote(String note) {
            this.note = note;
        }
    }
}
