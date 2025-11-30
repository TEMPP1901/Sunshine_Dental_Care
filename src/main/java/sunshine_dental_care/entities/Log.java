package sunshine_dental_care.entities;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "Logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Log {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notificationId")
    private Integer id;

    // --- THAY ĐỔI: THÊM QUAN HỆ USER ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;
    
    // Read-only field để tương thích với code cũ
    @Column(name = "userId", nullable = false, insertable = false, updatable = false)
    private Integer userId;

    @Column(name = "type", nullable = false, length = 50)
    private String type; // APPOINTMENT_CREATED, ATTENDANCE_CHECKIN, etc.

    @Column(name = "priority", nullable = false, length = 20)
    private String priority; // HIGH, MEDIUM, LOW
    // --- THAY ĐỔI: THÊM QUAN HỆ CLINIC ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinicId")
    private Clinic clinic;

    @Nationalized
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Nationalized
    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @ColumnDefault("0")
    @Column(name = "isRead")
    @lombok.Builder.Default
    private Boolean isRead = false;

    @Column(name = "readAt")
    private Instant readAt;

    @Nationalized
    @Column(name = "actionUrl", length = 500)
    private String actionUrl;

    @Column(name = "relatedEntityType", length = 50)
    private String relatedEntityType;

    @Column(name = "relatedEntityId")
    private Integer relatedEntityId;

    @Column(name = "action", length = 100)
    private String action;

    @Column(name = "tableName", length = 100)
    private String tableName;

    @Column(name = "recordId")
    private Integer recordId;

    @Nationalized
    @Column(name = "afterData", length = 2000)
    private String afterData;

    @Column(name = "ipAddr", length = 50)
    private String ipAddr;

    @Column(name = "userAgent", length = 500)
    private String userAgent;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "createdAt", nullable = false)
    private Instant createdAt;
    @Column(name = "actionTime", nullable = false)
    private Instant actionTime;

    @Column(name = "expiresAt")
    private Instant expiresAt;

    // --- LIFECYCLE CALLBACK ---
    @PrePersist
    public void prePersist() {
        if (actionTime == null) actionTime = Instant.now();
        if (createdAt == null)
            createdAt = Instant.now();
        if (isRead == null)
            isRead = false;
    }

    // --- GETTERS & SETTERS ---

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public void setClinic(Clinic clinic) {
        this.clinic = clinic;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Integer getRecordId() {
        return recordId;
    }

    public void setRecordId(Integer recordId) {
        this.recordId = recordId;
    }

    public String getAfterData() {
        return afterData;
    }

    public void setAfterData(String afterData) {
        this.afterData = afterData;
    }

    public String getIpAddr() {
        return ipAddr;
    }

    public void setIpAddr(String ipAddr) {
        this.ipAddr = ipAddr;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Instant getActionTime() {
        return actionTime;
    }

    public void setActionTime(Instant actionTime) {
        this.actionTime = actionTime;
    }
}