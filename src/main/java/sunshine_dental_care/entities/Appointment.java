package sunshine_dental_care.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

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
    @JoinColumn(name = "serviceId")
    private Service service;

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

    // --- Cờ đánh dấu đã gửi nhắc nhở 24h ---
    @Column(name = "is_reminder_sent")
    private Boolean isReminderSent = false;

    // --- MỚI THÊM: Cờ đánh dấu đã gửi nhắc nhở gấp (2h) ---
    @Column(name = "is_urgent_reminder_sent")
    private Boolean isUrgentReminderSent = false;

    @Column(name = "paymentStatus", length = 50)
    private String paymentStatus; // UNPAID, PAID

    @Column(name = "transactionRef", length = 100)
    private String transactionRef; // Mã giao dịch VNPay/PayPal

    @Column(name = "sub_total")
    private java.math.BigDecimal subTotal;

    // 2. Số tiền được giảm giá
    @Column(name = "discount_amount")
    private java.math.BigDecimal discountAmount;

    // 3. Tổng tiền khách thực trả (Sau khi trừ giảm giá + cọc)
    @Column(name = "total_amount")
    private java.math.BigDecimal totalAmount;

    @Column(name = "invoice_code")
    private String invoiceCode;

    // Mỗi Lịch hẹn (Appointment) có thể chứa nhiều (Many) bản ghi Chi tiết Dịch vụ Lịch hẹn (AppointmentService).
    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AppointmentService> appointmentServices;


    // --- GETTERS & SETTERS ---

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Clinic getClinic() { return clinic; }
    public void setClinic(Clinic clinic) { this.clinic = clinic; }

    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }

    public User getDoctor() { return doctor; }
    public void setDoctor(User doctor) { this.doctor = doctor; }

    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public Instant getStartDateTime() { return startDateTime; }
    public void setStartDateTime(Instant startDateTime) { this.startDateTime = startDateTime; }

    public Instant getEndDateTime() { return endDateTime; }
    public void setEndDateTime(Instant endDateTime) { this.endDateTime = endDateTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<AppointmentService> getAppointmentServices() { return appointmentServices; }
    public void setAppointmentServices(List<AppointmentService> appointmentServices) { this.appointmentServices = appointmentServices; }

    public String getAppointmentType() { return appointmentType; }
    public void setAppointmentType(String appointmentType) { this.appointmentType = appointmentType; }

    public BigDecimal getBookingFee() { return bookingFee; }
    public void setBookingFee(BigDecimal bookingFee) { this.bookingFee = bookingFee; }

    public Boolean getIsReminderSent() { return isReminderSent; }
    public void setIsReminderSent(Boolean reminderSent) { isReminderSent = reminderSent; }

    public Boolean getIsUrgentReminderSent() { return isUrgentReminderSent; }
    public void setIsUrgentReminderSent(Boolean urgentReminderSent) { isUrgentReminderSent = urgentReminderSent; }

    public String getPaymentStatus() {
        return paymentStatus;
    }
    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
    public String getTransactionRef() {
        return transactionRef;
    }
    public void setTransactionRef(String transactionRef) {
        this.transactionRef = transactionRef;
    }

    public java.math.BigDecimal getSubTotal() {
        return subTotal;
    }
    public void setSubTotal(java.math.BigDecimal subTotal) {
        this.subTotal = subTotal;
    }
    public java.math.BigDecimal getDiscountAmount() {
        return discountAmount;
    }
    public void setDiscountAmount(java.math.BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }
    public java.math.BigDecimal getTotalAmount() {
        return totalAmount;
    }
    public void setTotalAmount(java.math.BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getInvoiceCode() {
        return invoiceCode;
    }

    public void setInvoiceCode(String invoiceCode) {
        this.invoiceCode = invoiceCode;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (channel == null) channel = "WALK_IN";
        if (isReminderSent == null) isReminderSent = false;
        // Init cờ mới
        if (isUrgentReminderSent == null) isUrgentReminderSent = false;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}