package sunshine_dental_care.dto.hrDTO;

public class DepartmentResponse {
    private Integer id;
    private String departmentName;
    
    public DepartmentResponse() {}
    
    public DepartmentResponse(Integer id, String departmentName) {
        this.id = id;
        this.departmentName = departmentName;
    }
    
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getDepartmentName() {
        return departmentName;
    }
    
    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }
}

