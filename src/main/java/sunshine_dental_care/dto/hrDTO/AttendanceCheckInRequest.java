package sunshine_dental_care.dto.hrDTO;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

/**
 * DTO cho request chấm công vào (check-in)
 * Gửi từ mobile app
 */
public class AttendanceCheckInRequest {
    
    @NotNull(message = "User ID is required")
    private Integer userId;
    
    @NotNull(message = "Clinic ID is required")
    private Integer clinicId;
    
    /**
     * Face embedding từ ArcFace model (JSON array string)
     * Format: "[0.123, 0.456, 0.789, ...]" (512 dimensions)
     */
    @NotNull(message = "Face embedding is required")
    private String faceEmbedding;
    
    /**
     * WiFi SSID từ mobile device
     */
    private String ssid;
    
    /**
     * WiFi BSSID (MAC address) từ mobile device
     */
    private String bssid;
    
    /**
     * Device ID để tracking
     */
    private String deviceId;
    
    /**
     * IP Address của mobile device
     */
    private String ipAddr;
    
    /**
     * GPS Latitude
     */
    private BigDecimal lat;
    
    /**
     * GPS Longitude
     */
    private BigDecimal lng;
    
    /**
     * Optional note
     */
    private String note;
    
    // Getters and Setters
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

