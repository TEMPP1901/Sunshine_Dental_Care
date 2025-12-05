package sunshine_dental_care.dto.hrDTO;

public class ClinicResponse {
    private Integer id;
    private String clinicCode; // THÊM VÀO CHO reception
    private String clinicName;
    
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
}

