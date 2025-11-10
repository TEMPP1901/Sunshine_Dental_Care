package sunshine_dental_care.dto.hrDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class DoctorScheduleDto {
    private Integer id;
    private Object doctor;        // Sử dụng Object thay vì UserDto
    private Object clinic;        // Sử dụng Object thay vì ClinicDto
    private Object room;          // Sử dụng Object thay vì RoomDto
    private Object chair;         // Sử dụng Object thay vì ChairDto
    private LocalDate workDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Constructors
    public DoctorScheduleDto() {}
    
    public DoctorScheduleDto(Integer id, Object doctor, Object clinic, 
                           Object room, Object chair, LocalDate workDate, 
                           LocalTime startTime, LocalTime endTime, String status, 
                           String note, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.doctor = doctor;
        this.clinic = clinic;
        this.room = room;
        this.chair = chair;
        this.workDate = workDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.note = note;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // Getters and Setters
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public Object getDoctor() {
        return doctor;
    }
    
    public void setDoctor(Object doctor) {
        this.doctor = doctor;
    }
    
    public Object getClinic() {
        return clinic;
    }
    
    public void setClinic(Object clinic) {
        this.clinic = clinic;
    }
    
    public Object getRoom() {
        return room;
    }
    
    public void setRoom(Object room) {
        this.room = room;
    }
    
    public Object getChair() {
        return chair;
    }
    
    public void setChair(Object chair) {
        this.chair = chair;
    }
    
    public LocalDate getWorkDate() {
        return workDate;
    }
    
    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getNote() {
        return note;
    }
    
    public void setNote(String note) {
        this.note = note;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}