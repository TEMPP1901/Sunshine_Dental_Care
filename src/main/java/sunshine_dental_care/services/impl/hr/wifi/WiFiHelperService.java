package sunshine_dental_care.services.impl.hr.wifi;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WiFiHelperService {

    // Lấy thông tin WiFi thực khi chạy trên emulator hoặc không gửi WiFi
    public WiFiInfo resolveWiFiInfo(String ssid, String bssid) {
        boolean isEmulatorWiFi = isEmulatorWiFi(ssid, bssid);
        boolean hasNoWiFi = (ssid == null || ssid.trim().isEmpty()) && (bssid == null || bssid.trim().isEmpty());

        if (isEmulatorWiFi || hasNoWiFi) {
            return getWiFiFromHostMachine(ssid, bssid);
        }

        return new WiFiInfo(ssid, bssid);
    }

    // true nếu là WiFi emulator hoặc chuỗi nhận diện máy ảo
    private boolean isEmulatorWiFi(String ssid, String bssid) {
        return (ssid != null && (ssid.trim().equalsIgnoreCase("AndroidWifi") ||
                ssid.trim().equalsIgnoreCase("AndroidAP") ||
                ssid.trim().toUpperCase().startsWith("ANDROID"))) ||
                (bssid != null && bssid.trim().startsWith("00:13:10"));
    }

    // Lấy WiFi từ máy host, fallback khi dùng máy ảo hoặc không xác định được WiFi
    private WiFiInfo getWiFiFromHostMachine(String originalSsid, String originalBssid) {
        String ssid = originalSsid;
        String bssid = originalBssid;

        try {
            sunshine_dental_care.utils.WindowsWiFiUtil.WiFiInfo hostWiFi =
                    sunshine_dental_care.utils.WindowsWiFiUtil.getCurrentWiFiInfo();

            if (hostWiFi.getSsid() != null && !hostWiFi.getSsid().trim().isEmpty()) {
                ssid = hostWiFi.getSsid();
            }
            if (hostWiFi.getBssid() != null && !hostWiFi.getBssid().trim().isEmpty()) {
                bssid = hostWiFi.getBssid();
            }
        } catch (Exception e) {
            log.error("Failed to get WiFi info from host machine: {}", e.getMessage());
        }

        return new WiFiInfo(ssid, bssid);
    }

    // Data class thông tin WiFi (SSID, BSSID)
    public static class WiFiInfo {
        private final String ssid;
        private final String bssid;

        public WiFiInfo(String ssid, String bssid) {
            this.ssid = ssid;
            this.bssid = bssid;
        }

        public String getSsid() {
            return ssid;
        }

        public String getBssid() {
            return bssid;
        }
    }
}
