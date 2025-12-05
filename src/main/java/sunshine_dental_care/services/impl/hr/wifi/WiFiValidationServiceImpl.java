package sunshine_dental_care.services.impl.hr.wifi;

import java.util.Collections;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.config.WiFiConfig;
import sunshine_dental_care.dto.hrDTO.WiFiValidationResult;
import sunshine_dental_care.services.interfaces.hr.WiFiValidationService;

@Service
@RequiredArgsConstructor
@Slf4j
public class WiFiValidationServiceImpl implements WiFiValidationService {

    private final WiFiConfig wifiConfig;

    // Kiểm tra hợp lệ WiFi cho clinic cụ thể
    @Override
    public WiFiValidationResult validateWiFi(String ssid, String bssid, Integer clinicId) {
        log.info("Validating WiFi for clinic {}: SSID={}, BSSID={}", clinicId, ssid, bssid);

        boolean isValid = wifiConfig.isWiFiAllowedForClinic(ssid, bssid, clinicId);

        boolean ssidValid = wifiConfig.isSsidAllowed(ssid);
        boolean bssidValid = wifiConfig.isBssidAllowed(bssid);

        WiFiConfig.ClinicWiFiConfig clinicConfig = wifiConfig.getClinic().get(String.valueOf(clinicId));
        if (clinicConfig != null) {
            // Nếu có config riêng cho clinic thì ưu tiên kiểm tra theo danh sách riêng
            java.util.List<String> allowedSsids = parseList(clinicConfig.getSsids());
            java.util.List<String> allowedBssids = parseList(clinicConfig.getBssids());

            ssidValid = ssid != null && !ssid.trim().isEmpty()
                    && allowedSsids.contains(ssid.trim().toUpperCase());
            bssidValid = bssid != null && !bssid.trim().isEmpty()
                    && allowedBssids.contains(bssid.trim().toUpperCase());
        }

        String message;
        if (isValid) {
            message = String.format("WiFi validated successfully for clinic %d", clinicId);
        } else {
            message = String.format(
                    "WiFi validation failed for clinic %d. SSID or BSSID not in whitelist. SSID valid: %s, BSSID valid: %s",
                    clinicId, ssidValid, bssidValid);
        }

        log.info(
                "WiFi validation result for clinic {}: valid={}, SSID valid={}, BSSID valid={}, SSID={}, BSSID={}",
                clinicId, isValid, ssidValid, bssidValid, ssid, bssid);

        return new WiFiValidationResult(isValid, ssidValid, bssidValid, ssid, bssid, clinicId, message);
    }

    // Chuyển chuỗi comma-separated thành list String, in hoa và loại bỏ rỗng
    private java.util.List<String> parseList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }
}
