package sunshine_dental_care.services.impl.hr;

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

    // Hàm validate SSID và BSSID cho 1 clinic cụ thể
    @Override
    public WiFiValidationResult validateWiFi(String ssid, String bssid, Integer clinicId) {
        log.info("Validating WiFi for clinic {}: SSID={}, BSSID={}", clinicId, ssid, bssid);

        boolean isValid = wifiConfig.isWiFiAllowedForClinic(ssid, bssid, clinicId);

        boolean ssidValid = wifiConfig.isSsidAllowed(ssid);
        boolean bssidValid = wifiConfig.isBssidAllowed(bssid);

        // Nếu có cấu hình wifi riêng cho clinic, sẽ kiểm tra lại với danh sách cấu hình riêng
        WiFiConfig.ClinicWiFiConfig clinicConfig = wifiConfig.getClinic().get(String.valueOf(clinicId));
        if (clinicConfig != null) {
            log.debug("Found clinic-specific WiFi config for clinic {}", clinicId);
            java.util.List<String> allowedSsids = parseList(clinicConfig.getSsids());
            java.util.List<String> allowedBssids = parseList(clinicConfig.getBssids());

            log.debug("Clinic {} allowed SSIDs: {}, allowed BSSIDs: {}", clinicId, allowedSsids, allowedBssids);

            ssidValid = ssid != null && !ssid.trim().isEmpty()
                    && allowedSsids.contains(ssid.trim().toUpperCase());
            bssidValid = bssid != null && !bssid.trim().isEmpty()
                    && allowedBssids.contains(bssid.trim().toUpperCase());
        } else {
            log.debug("No clinic-specific WiFi config found for clinic {}, using global config", clinicId);
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

    // Hàm tách chuỗi thành List<String> từ chuỗi phân cách bởi dấu phẩy
    private java.util.List<String> parseList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }
}
