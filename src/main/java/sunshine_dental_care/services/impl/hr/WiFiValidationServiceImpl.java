package sunshine_dental_care.services.impl.hr;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.config.WiFiConfig;
import sunshine_dental_care.dto.hrDTO.WiFiValidationResult;
import sunshine_dental_care.services.interfaces.hr.WiFiValidationService;

/**
 * Implementation của WiFiValidationService
 * Validate SSID và BSSID theo config trong properties
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WiFiValidationServiceImpl implements WiFiValidationService {

    private final WiFiConfig wifiConfig;

    @Override
    public WiFiValidationResult validateWiFi(String ssid, String bssid, Integer clinicId) {
        log.info("Validating WiFi for clinic {}: SSID={}, BSSID={}", clinicId, ssid, bssid);

        // Validate WiFi theo clinic cụ thể
        boolean isValid = wifiConfig.isWiFiAllowedForClinic(ssid, bssid, clinicId);

        // Check từng phần để có message chi tiết
        boolean ssidValid = wifiConfig.isSsidAllowed(ssid);
        boolean bssidValid = wifiConfig.isBssidAllowed(bssid);

        // Nếu có config riêng cho clinic, check lại
        WiFiConfig.ClinicWiFiConfig clinicConfig = wifiConfig.getClinic().get(String.valueOf(clinicId));
        if (clinicConfig != null) {
            log.debug("Found clinic-specific WiFi config for clinic {}", clinicId);
            // Có config riêng cho clinic, validate theo config đó
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
            message = String.format("WiFi validation failed for clinic %d. SSID or BSSID not in whitelist. SSID valid: %s, BSSID valid: %s",
                    clinicId, ssidValid, bssidValid);
        }

        log.info("WiFi validation result for clinic {}: valid={}, SSID valid={}, BSSID valid={}, SSID={}, BSSID={}",
                clinicId, isValid, ssidValid, bssidValid, ssid, bssid);

        return new WiFiValidationResult(isValid, ssidValid, bssidValid, ssid, bssid, clinicId, message);
    }

    /**
     * Parse comma-separated string thành List
     */
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
