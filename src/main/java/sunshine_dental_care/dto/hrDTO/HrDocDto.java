package sunshine_dental_care.dto.hrDTO;

import java.util.List;

public class HrDocDto {
    private Integer id;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private String code;

    private String specialty;
    private DepartmentResponse department;
    private List<RoleResponse> userRoles;
    
    // Constructors
    public HrDocDto() {}
    
    public HrDocDto(Integer id, String fullName, String email, String phone, String avatarUrl, String code, String specialty, DepartmentResponse department, List<RoleResponse> userRoles) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.avatarUrl = avatarUrl;
        this.code = code;
        this.specialty = specialty;
        this.department = department;
        this.userRoles = userRoles;
    }

    public HrDocDto(Integer id, String fullName, String email, String phone, String avatarUrl, String code) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.avatarUrl = avatarUrl;
        this.code = code;
    }
    
    // Getters and Setters
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
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
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public DepartmentResponse getDepartment() {
        return department;
    }

    public void setDepartment(DepartmentResponse department) {
        this.department = department;
    }

    public List<RoleResponse> getUserRoles() {
        return userRoles;
    }

    public void setUserRoles(List<RoleResponse> userRoles) {
        this.userRoles = userRoles;
    }
}

