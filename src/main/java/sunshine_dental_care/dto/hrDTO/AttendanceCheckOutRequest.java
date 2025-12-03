package sunshine_dental_care.dto.hrDTO;

import jakarta.validation.constraints.NotNull;

/**
 * DTO cho request chấm công ra (check-out)
 * Xử lý giống check-in: face verification + WiFi validation
 */
public class AttendanceCheckOutRequest {
    
    @NotNull(message = "Attendance ID is required")
    private Integer attendanceId;
    
    /**
     * Face embedding từ ArcFace model (JSON array string)
     * Format: "[0.123, 0.456, 0.789, ...]" (512 dimensions)
     * Required để verify face khi check-out
     */
    @NotNull(message = "Face embedding is required")
    private String faceEmbedding;
    
    /**
     * WiFi SSID từ mobile device (optional nhưng nên có để validate)
     */
    private String ssid;
    
    /**
     * WiFi BSSID (MAC address) từ mobile device (optional nhưng nên có để validate)
     */
    private String bssid;
    
    /**
     * Optional note khi check-out
     */
    private String note;
    
    // Getters and Setters
    public Integer getAttendanceId() {
        return attendanceId;
    }
    
    public void setAttendanceId(Integer attendanceId) {
        this.attendanceId = attendanceId;
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
    
    public String getNote() {
        return note;
    }
    
    public void setNote(String note) {
        this.note = note;
    }
}

