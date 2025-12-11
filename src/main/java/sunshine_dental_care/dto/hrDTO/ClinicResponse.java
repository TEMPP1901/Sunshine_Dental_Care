package sunshine_dental_care.dto.hrDTO;

public class ClinicResponse {
    private Integer id;
    private String clinicCode; // THÊM VÀO CHO reception
    private String clinicName;
    private Boolean isActive; // Trạng thái hoạt động của clinic
    
    public ClinicResponse() {}
    
    public ClinicResponse(Integer id, String clinicName, String clinicCode) {
        this.id = id;
        this.clinicName = clinicName;
        this.clinicCode = clinicCode;
    }

    public ClinicResponse(Integer id, String clinicName) {
        this.id = id;
        this.clinicName = clinicName;
    }

    public ClinicResponse(Integer id, String clinicName, Boolean isActive) {
        this.id = id;
        this.clinicName = clinicName;
        this.isActive = isActive;
    }

    public ClinicResponse(Integer id, String clinicName, String clinicCode, Boolean isActive) {
        this.id = id;
        this.clinicName = clinicName;
        this.clinicCode = clinicCode;
        this.isActive = isActive;
    }
    
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getClinicName() {
        return clinicName;
    }
    
    public void setClinicName(String clinicName) {
        this.clinicName = clinicName;
    }

    public String getClinicCode() {
        return clinicCode;
    }

    public void setClinicCode(String clinicCode) {
        this.clinicCode = clinicCode;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}

