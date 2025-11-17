package sunshine_dental_care.dto.hrDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class DoctorScheduleDto {
    private Integer id;
    private HrDocDto doctor;           // DTO cho doctor
    private ClinicResponse clinic;     // DTO cho clinic
    private RoomResponse room;         // DTO cho room
    private LocalDate workDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Constructors
    public DoctorScheduleDto() {}
    
    public DoctorScheduleDto(Integer id, HrDocDto doctor, ClinicResponse clinic, 
                           RoomResponse room, LocalDate workDate, 
                           LocalTime startTime, LocalTime endTime, String status, 
                           String note, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.doctor = doctor;
        this.clinic = clinic;
        this.room = room;
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
    
    public HrDocDto getDoctor() {
        return doctor;
    }
    
    public void setDoctor(HrDocDto doctor) {
        this.doctor = doctor;
    }
    
    public ClinicResponse getClinic() {
        return clinic;
    }
    
    public void setClinic(ClinicResponse clinic) {
        this.clinic = clinic;
    }
    
    public RoomResponse getRoom() {
        return room;
    }
    
    public void setRoom(RoomResponse room) {
        this.room = room;
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