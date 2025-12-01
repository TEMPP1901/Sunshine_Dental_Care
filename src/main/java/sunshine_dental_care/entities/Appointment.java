package sunshine_dental_care.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Appointments")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointmentId", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinicId", nullable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patientId", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "doctorId", nullable = true)
    private User doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roomId")
    private Room room;

    @Column(name = "startDateTime", nullable = false)
    private Instant startDateTime;

    @Column(name = "endDateTime")
    private Instant endDateTime;

    @Nationalized
    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Nationalized
    @Column(name = "channel", length = 50)
    private String channel;

    @Nationalized
    @Column(name = "note", length = 400)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "createdBy")
    private User createdBy;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "createdAt", nullable = false)
    private Instant createdAt;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "updatedAt", nullable = false)
    private Instant updatedAt;

    @Nationalized
    @Column(name = "appointmentType", length = 50)
    private String appointmentType; // "VIP" hoặc "STANDARD"

    @Column(name = "bookingFee")
    private BigDecimal bookingFee; // Phí đặt lịch hẹn

    // Mỗi Lịch hẹn (Appointment) có thể chứa nhiều (Many) bản ghi Chi tiết Dịch vụ Lịch hẹn (AppointmentService).
    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AppointmentService> appointmentServices;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public void setClinic(Clinic clinic) {
        this.clinic = clinic;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public User getDoctor() {
        return doctor;
    }

    public void setDoctor(User doctor) {
        this.doctor = doctor;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public Instant getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(Instant startDateTime) {
        this.startDateTime = startDateTime;
    }

    public Instant getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(Instant endDateTime) {
        this.endDateTime = endDateTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }


    public List<AppointmentService> getAppointmentServices() {
        return appointmentServices;
    }

    public void setAppointmentServices(List<AppointmentService> appointmentServices) {
        this.appointmentServices = appointmentServices;
    }

    public String getAppointmentType() { return appointmentType; }
    public void setAppointmentType(String appointmentType) { this.appointmentType = appointmentType; }

    public BigDecimal getBookingFee() { return bookingFee; }
    public void setBookingFee(BigDecimal bookingFee) { this.bookingFee = bookingFee; }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        // Mặc định channel nếu chưa có
        if (channel == null) {
            channel = "WALK_IN";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

}