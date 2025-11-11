package sunshine_dental_care.services.interfaces.hr;

import sunshine_dental_care.dto.hrDTO.WiFiValidationResult;

/**
 * Service interface để validate WiFi (SSID & BSSID) cho attendance
 */
public interface WiFiValidationService {
    
    /**
     * Validate WiFi theo clinic cụ thể
     * @param ssid WiFi network name từ mobile
     * @param bssid MAC address từ mobile
     * @param clinicId ID của clinic đang chấm công
     * @return WiFiValidationResult chứa kết quả validation
     */
    WiFiValidationResult validateWiFi(String ssid, String bssid, Integer clinicId);
}

