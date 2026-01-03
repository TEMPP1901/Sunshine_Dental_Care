package sunshine_dental_care.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import lombok.extern.slf4j.Slf4j;

// Class utility lấy SSID và BSSID hiện tại từ Windows
@Slf4j
public class WindowsWiFiUtil {

    // Lấy SSID hiện tại trên Windows
    public static String getCurrentSSID() {
        try {
            Process process = new ProcessBuilder("netsh", "wlan", "show", "interfaces").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Lấy thông tin SSID từ dòng bắt đầu bằng "SSID"
                    if (line.trim().startsWith("SSID")) {
                        String[] parts = line.split(":");
                        if (parts.length > 1) {
                            String ssid = parts[1].trim();
                            log.debug("Found SSID: {}", ssid);
                            process.waitFor();
                            return ssid;
                        }
                    }
                }
            }
            process.waitFor();
        } catch (java.io.IOException | InterruptedException e) {
            log.warn("Không lấy được SSID từ Windows: {}", e.getMessage());
        }
        return null;
    }

    // Lấy BSSID (địa chỉ MAC) trên Windows
    public static String getCurrentBSSID() {
        try {
            Process process = new ProcessBuilder("netsh", "wlan", "show", "interfaces").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Lấy thông tin BSSID từ dòng bắt đầu bằng "BSSID"
                    if (line.trim().startsWith("BSSID")) {
                        String[] parts = line.split(":");
                        if (parts.length > 1) {
                            String bssid = parts[1].trim();
                            log.debug("Found BSSID: {}", bssid);
                            process.waitFor();
                            return bssid;
                        }
                    }
                }
            }
            process.waitFor();
        } catch (java.io.IOException | InterruptedException e) {
            log.warn("Không lấy được BSSID từ Windows: {}", e.getMessage());
        }
        return null;
    }

    // Lấy cả SSID và BSSID
    public static WiFiInfo getCurrentWiFiInfo() {
        String ssid = getCurrentSSID();
        String bssid = getCurrentBSSID();
        return new WiFiInfo(ssid, bssid);
    }

    // Class chứa thông tin WiFi
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
