package sunshine_dental_care.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration class để đọc WiFi settings từ application.properties
 * Hỗ trợ config global và config theo từng clinic
 */
@Configuration
@ConfigurationProperties(prefix = "app.attendance.wifi")
@Getter
@Setter
public class WiFiConfig {
    
    /**
     * Global WiFi config (fallback nếu không có config riêng cho clinic)
     * Danh sách SSID được phép (comma-separated)
     */
    private String allowedSsids = "";
    
    /**
     * Global WiFi config (fallback)
     * Danh sách BSSID được phép (comma-separated)
     */
    private String allowedBssids = "";
    
    /**
     * WiFi config theo clinic (Map<clinicId, ClinicWiFiConfig>)
     * Key: clinicId (String), Value: ClinicWiFiConfig
     */
    private Map<String, ClinicWiFiConfig> clinic = new HashMap<>();
    
    /**
     * Inner class để lưu config cho từng clinic
     */
    @Getter
    @Setter
    public static class ClinicWiFiConfig {
        private String ssids = "";
        private String bssids = "";
    }
    
    /**
     * Lấy danh sách SSID dưới dạng List (đã trim và uppercase)
     */
    public List<String> getAllowedSsidsList() {
        if (allowedSsids == null || allowedSsids.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(allowedSsids.split(","))
            .map(String::trim)
            .map(String::toUpperCase)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
    
    /**
     * Lấy danh sách BSSID dưới dạng List (đã trim và uppercase)
     */
    public List<String> getAllowedBssidsList() {
        if (allowedBssids == null || allowedBssids.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(allowedBssids.split(","))
            .map(String::trim)
            .map(String::toUpperCase)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
    
    /**
     * Kiểm tra SSID có trong whitelist không
     */
    public boolean isSsidAllowed(String ssid) {
        if (ssid == null || ssid.trim().isEmpty()) {
            return false;
        }
        List<String> allowedList = getAllowedSsidsList();
        if (allowedList.isEmpty()) {
            return false; // Nếu không config thì không cho phép
        }
        return allowedList.contains(ssid.trim().toUpperCase());
    }
    
    /**
     * Kiểm tra BSSID có trong whitelist không
     */
    public boolean isBssidAllowed(String bssid) {
        if (bssid == null || bssid.trim().isEmpty()) {
            return false;
        }
        List<String> allowedList = getAllowedBssidsList();
        if (allowedList.isEmpty()) {
            return false; // Nếu không config thì không cho phép
        }
        return allowedList.contains(bssid.trim().toUpperCase());
    }
    
    /**
     * Kiểm tra SSID hoặc BSSID có match không (dùng cho validation)
     * Trả về true nếu ít nhất một trong hai match
     */
    public boolean isWiFiAllowed(String ssid, String bssid) {
        return isSsidAllowed(ssid) || isBssidAllowed(bssid);
    }
    
    /**
     * Kiểm tra WiFi theo clinic cụ thể
     * Nếu có config riêng cho clinic thì dùng, không thì dùng global config
     */
    public boolean isWiFiAllowedForClinic(String ssid, String bssid, Integer clinicId) {
        if (clinicId == null) {
            // Nếu không có clinicId, dùng global config
            return isWiFiAllowed(ssid, bssid);
        }
        
        // Kiểm tra có config riêng cho clinic không
        ClinicWiFiConfig clinicConfig = clinic.get(String.valueOf(clinicId));
        if (clinicConfig != null) {
            // Dùng config riêng của clinic
            List<String> allowedSsids = parseList(clinicConfig.getSsids());
            List<String> allowedBssids = parseList(clinicConfig.getBssids());
            
            boolean ssidMatch = ssid != null && !ssid.trim().isEmpty() 
                && allowedSsids.contains(ssid.trim().toUpperCase());
            boolean bssidMatch = bssid != null && !bssid.trim().isEmpty() 
                && allowedBssids.contains(bssid.trim().toUpperCase());
            
            return ssidMatch || bssidMatch;
        }
        
        // Không có config riêng, dùng global config
        return isWiFiAllowed(ssid, bssid);
    }
    
    /**
     * Parse comma-separated string thành List
     */
    private List<String> parseList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .map(String::toUpperCase)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
}

