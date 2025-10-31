package sunshine_dental_care.dto.hrDTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class EmployeeRequest {
    
    @NotBlank(message = "Full name is required")
    private String fullName;
    
    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;
    
    @Pattern(regexp = "^[0-9]{10,11}$", message = "Phone must be 10-11 digits")
    private String phone;
    
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;
    
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;          // Chỉ cần khi tạo mới
    
    private String code;
    private String avatarUrl;
    
    private Integer departmentId;
    
    @NotNull(message = "Role ID is required for new employee")
    private Integer roleId;           // Chỉ cần khi tạo mới
    
    @NotNull(message = "Clinic ID is required for new employee")
    private Integer clinicId;         // Chỉ cần khi tạo mới
    private String roleAtClinic;
    private String description;
    
    // Constructors
    public EmployeeRequest() {}
    
    public EmployeeRequest(String fullName, String email, String phone, 
                          String username, String password, String code,
                          String avatarUrl, Integer departmentId, Integer roleId,
                          Integer clinicId, String roleAtClinic, String description) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.username = username;
        this.password = password;
        this.code = code;
        this.avatarUrl = avatarUrl;
        this.departmentId = departmentId;
        this.roleId = roleId;
        this.clinicId = clinicId;
        this.roleAtClinic = roleAtClinic;
        this.description = description;
    }
    
    // Getters and Setters
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
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    
    public Integer getDepartmentId() {
        return departmentId;
    }
    
    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }
    
    public Integer getRoleId() {
        return roleId;
    }
    
    public void setRoleId(Integer roleId) {
        this.roleId = roleId;
    }
    
    public Integer getClinicId() {
        return clinicId;
    }
    
    public void setClinicId(Integer clinicId) {
        this.clinicId = clinicId;
    }
    
    public String getRoleAtClinic() {
        return roleAtClinic;
    }
    
    public void setRoleAtClinic(String roleAtClinic) {
        this.roleAtClinic = roleAtClinic;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
}
