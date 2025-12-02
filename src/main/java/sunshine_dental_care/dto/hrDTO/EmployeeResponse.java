package sunshine_dental_care.dto.hrDTO;

import java.time.LocalDateTime;
import java.util.List;

public class EmployeeResponse {
    private Integer id;
    private String code;
    private String fullName;
    private String email;
    private String phone;
    private String username;
    private String avatarUrl;
    private Boolean isActive;
    private String faceImageUrl;
    private String faceEmbedding;
    private Object department;        
    private Object role;              
    private Object clinic;            
    private String roleAtClinic;
    private String specialty;         // Specialty for doctors (backward compatibility)
    private List<String> specialties;  // Multiple specialties for doctors
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    
    // Constructors
    public EmployeeResponse() {}
    
    public EmployeeResponse(Integer id, String code, String fullName, String email,
                           String phone, String username, String avatarUrl, Boolean isActive,
                           String faceImageUrl, String faceEmbedding,
                           Object department, Object role, Object clinic, String roleAtClinic,
                           LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime lastLoginAt) {
        this.id = id;
        this.code = code;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.isActive = isActive;
        this.faceImageUrl = faceImageUrl;
        this.faceEmbedding = faceEmbedding;
        this.department = department;
        this.role = role;
        this.clinic = clinic;
        this.roleAtClinic = roleAtClinic;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastLoginAt = lastLoginAt;
    }
    
    // Getters and Setters
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    public String getFaceImageUrl() {
        return faceImageUrl;
    }
    
    public void setFaceImageUrl(String faceImageUrl) {
        this.faceImageUrl = faceImageUrl;
    }
    
    public String getFaceEmbedding() {
        return faceEmbedding;
    }
    
    public void setFaceEmbedding(String faceEmbedding) {
        this.faceEmbedding = faceEmbedding;
    }
    
    public Object getDepartment() {
        return department;
    }
    
    public void setDepartment(Object department) {
        this.department = department;
    }
    
    public Object getRole() {
        return role;
    }
    
    public void setRole(Object role) {
        this.role = role;
    }
    
    public Object getClinic() {
        return clinic;
    }
    
    public void setClinic(Object clinic) {
        this.clinic = clinic;
    }
    
    public String getRoleAtClinic() {
        return roleAtClinic;
    }
    
    public void setRoleAtClinic(String roleAtClinic) {
        this.roleAtClinic = roleAtClinic;
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
    
    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }
    
    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
    
    public String getSpecialty() {
        return specialty;
    }
    
    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public List<String> getSpecialties() {
        return specialties;
    }

    public void setSpecialties(List<String> specialties) {
        this.specialties = specialties;
    }
}
