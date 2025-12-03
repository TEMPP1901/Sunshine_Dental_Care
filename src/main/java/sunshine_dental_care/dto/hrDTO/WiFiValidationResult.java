package sunshine_dental_care.dto.hrDTO;

/**
 * DTO cho kết quả validation WiFi
 */
public class WiFiValidationResult {
    
    private boolean valid;
    private boolean ssidValid;
    private boolean bssidValid;
    private String ssid;
    private String bssid;
    private Integer clinicId;
    private String message;
    
    public WiFiValidationResult() {}
    
    public WiFiValidationResult(boolean valid, boolean ssidValid, boolean bssidValid, 
                                String ssid, String bssid, Integer clinicId, String message) {
        this.valid = valid;
        this.ssidValid = ssidValid;
        this.bssidValid = bssidValid;
        this.ssid = ssid;
        this.bssid = bssid;
        this.clinicId = clinicId;
        this.message = message;
    }
    
    // Getters and Setters
    public boolean isValid() {
        return valid;
    }
    
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    
    public boolean isSsidValid() {
        return ssidValid;
    }
    
    public void setSsidValid(boolean ssidValid) {
        this.ssidValid = ssidValid;
    }
    
    public boolean isBssidValid() {
        return bssidValid;
    }
    
    public void setBssidValid(boolean bssidValid) {
        this.bssidValid = bssidValid;
    }
    
    public String getSsid() {
        return ssid;
    }
    
    public void setSsid(String ssid) {
        this.ssid = ssid;
    }
    
    public String getBssid() {
        return bssid;
    }
    
    public void setBssid(String bssid) {
        this.bssid = bssid;
    }
    
    public Integer getClinicId() {
        return clinicId;
    }
    
    public void setClinicId(Integer clinicId) {
        this.clinicId = clinicId;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}

