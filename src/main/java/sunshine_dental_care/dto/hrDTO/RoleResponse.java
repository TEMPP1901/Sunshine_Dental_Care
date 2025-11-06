package sunshine_dental_care.dto.hrDTO;

public class RoleResponse {
    private Integer id;
    private String roleName;
    private String description;
    
    public RoleResponse() {}
    
    public RoleResponse(Integer id, String roleName) {
        this.id = id;
        this.roleName = roleName;
    }
    
    public RoleResponse(Integer id, String roleName, String description) {
        this.id = id;
        this.roleName = roleName;
        this.description = description;
    }
    
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getRoleName() {
        return roleName;
    }
    
    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
}

