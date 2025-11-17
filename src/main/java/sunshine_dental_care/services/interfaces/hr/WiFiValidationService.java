package sunshine_dental_care.services.interfaces.hr;

import sunshine_dental_care.dto.hrDTO.WiFiValidationResult;

public interface WiFiValidationService {

    // Xác thực kết nối WiFi cho từng phòng khám
    WiFiValidationResult validateWiFi(String ssid, String bssid, Integer clinicId);
}
