package sunshine_dental_care.dto.hrDTO;

public class ClinicResponse {
    private Integer id;
    private String clinicName;
    
    public ClinicResponse() {}
    
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
}

