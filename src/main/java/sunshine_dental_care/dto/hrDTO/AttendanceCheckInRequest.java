package sunshine_dental_care.dto.hrDTO;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

public class AttendanceCheckInRequest {

    @NotNull(message = "User ID is required")
    private Integer userId;

    // Clinic ID có thể null, sẽ tự động xác định qua UserClinicAssignment nếu không truyền
    private Integer clinicId;

    
    @NotNull(message = "Face embedding is required")
    private String faceEmbedding;

    private String ssid;
    private String bssid;
    private String deviceId;
    private String ipAddr;
    private BigDecimal lat;
    private BigDecimal lng;
    private String note;

    // Getter và setter cho từng trường
    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getClinicId() {
        return clinicId;
    }

    public void setClinicId(Integer clinicId) {
        this.clinicId = clinicId;
    }

    public String getFaceEmbedding() {
        return faceEmbedding;
    }

    public void setFaceEmbedding(String faceEmbedding) {
        this.faceEmbedding = faceEmbedding;
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

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getIpAddr() {
        return ipAddr;
    }

    public void setIpAddr(String ipAddr) {
        this.ipAddr = ipAddr;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public void setLat(BigDecimal lat) {
        this.lat = lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public void setLng(BigDecimal lng) {
        this.lng = lng;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
