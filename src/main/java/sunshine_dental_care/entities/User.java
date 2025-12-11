package sunshine_dental_care.entities;

import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
@Table(name = "Users")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "userId", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "code", length = 50)
    private String code;

    @Nationalized
    @Column(name = "fullName", nullable = false, length = 200)
    private String fullName;

    @Nationalized
    @Column(name = "username", length = 100)
    private String username;

    @Nationalized
    @Column(name = "passwordHash")
    private String passwordHash;

    @Nationalized
    @Column(name = "avatarUrl", length = 400)
    private String avatarUrl;

    @Nationalized
    @Column(name = "email", length = 120)
    private String email;

    @Nationalized
    @Column(name = "phone", length = 50)
    private String phone;

    @Nationalized
    @ColumnDefault("'local'")
    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Nationalized
    @Column(name = "providerId", length = 200)
    private String providerId;

    @Column(name = "avatarPublicId", length = 400)
    private String avatarPublicId;

    @ColumnDefault("1")
    @Column(name = "isActive", nullable = false)
    private Boolean isActive = false;

    // --- ĐẾM SỐ LẦN NHẬP SAI PASS ---
    @ColumnDefault("0")
    @Column(name = "failedLoginAttempts")
    private Integer failedLoginAttempts = 0;

    // --- TOKEN RESET PASSWORD ---
    @Column(name = "resetPasswordToken", length = 64)
    private String resetPasswordToken;

    @Column(name = "resetPasswordTokenExpiry")
    private Instant resetPasswordTokenExpiry;

    // --- [CỦA TUẤN] TOKEN XÁC THỰC TÀI KHOẢN (EMAIL) ---
    @Column(name = "verificationToken", length = 64)
    private String verificationToken;

    @Column(name = "verificationTokenExpiry")
    private Instant verificationTokenExpiry;

    // --- [CỦA TUẤN] MÃ OTP ĐỂ ĐĂNG NHẬP SĐT ---
    @Column(name = "otpCode", length = 10)
    private String otpCode;

    @Column(name = "otpExpiry")
    private Instant otpExpiry;

    // --- AUDIT FIELDS ---
    @ColumnDefault("sysutcdatetime()")
    @Column(name = "createdAt", nullable = false)
    private Instant createdAt;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "updatedAt", nullable = false)
    private Instant updatedAt;

    @Column(name = "lastLoginAt")
    private Instant lastLoginAt;

    // --- RELATIONS ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departmentId")
    private Department department;

    @Nationalized
    @Column(name = "specialty", length = 100)
    private String specialty;

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JsonIgnore // [QUAN TRỌNG] Thêm annotation này của Long để tránh lỗi Loop
    private List<DoctorSpecialty> doctorSpecialties;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @JsonIgnore // [QUAN TRỌNG] Thêm annotation này của Long để tránh lỗi Loop
    private List<UserRole> userRoles;

    // =================================================================
    // GETTERS & SETTERS (Đã gộp đầy đủ)
    // =================================================================

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getAvatarPublicId() { return avatarPublicId; }
    public void setAvatarPublicId(String avatarPublicId) { this.avatarPublicId = avatarPublicId; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Integer getFailedLoginAttempts() { return failedLoginAttempts == null ? 0 : failedLoginAttempts; }
    public void setFailedLoginAttempts(Integer failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }

    // Reset Password
    public String getResetPasswordToken() { return resetPasswordToken; }
    public void setResetPasswordToken(String resetPasswordToken) { this.resetPasswordToken = resetPasswordToken; }

    public Instant getResetPasswordTokenExpiry() { return resetPasswordTokenExpiry; }
    public void setResetPasswordTokenExpiry(Instant resetPasswordTokenExpiry) { this.resetPasswordTokenExpiry = resetPasswordTokenExpiry; }

    // Verify Account (Email) - [CỦA TUẤN]
    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }

    public Instant getVerificationTokenExpiry() { return verificationTokenExpiry; }
    public void setVerificationTokenExpiry(Instant verificationTokenExpiry) { this.verificationTokenExpiry = verificationTokenExpiry; }

    // OTP Login (Phone) - [CỦA TUẤN]
    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }

    public Instant getOtpExpiry() { return otpExpiry; }
    public void setOtpExpiry(Instant otpExpiry) { this.otpExpiry = otpExpiry; }

    // Audit
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    // Relations
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public List<DoctorSpecialty> getDoctorSpecialties() { return doctorSpecialties; }
    public void setDoctorSpecialties(List<DoctorSpecialty> doctorSpecialties) { this.doctorSpecialties = doctorSpecialties; }

    public List<UserRole> getUserRoles() { return userRoles; }
    public void setUserRoles(List<UserRole> userRoles) { this.userRoles = userRoles; }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
        if (failedLoginAttempts == null) failedLoginAttempts = 0;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}