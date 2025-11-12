package sunshine_dental_care.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class để lấy WiFi SSID và BSSID từ Windows system
 * Sử dụng lệnh netsh wlan show interfaces để lấy thông tin WiFi
 */
@Slf4j
public class WindowsWiFiUtil {
    
    /**
     * Lấy SSID của WiFi đang kết nối trên Windows
     * @return SSID hoặc null nếu không lấy được
     */
    public static String getCurrentSSID() {
        try {
            Process process = new ProcessBuilder("netsh", "wlan", "show", "interfaces").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
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
            log.warn("Failed to get SSID from Windows: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Lấy BSSID (MAC address) của WiFi đang kết nối trên Windows
     * @return BSSID hoặc null nếu không lấy được
     */
    public static String getCurrentBSSID() {
        try {
            Process process = new ProcessBuilder("netsh", "wlan", "show", "interfaces").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
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
            log.warn("Failed to get BSSID from Windows: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Lấy cả SSID và BSSID từ Windows
     * @return WiFiInfo object chứa SSID và BSSID
     */
    public static WiFiInfo getCurrentWiFiInfo() {
        String ssid = getCurrentSSID();
        String bssid = getCurrentBSSID();
        return new WiFiInfo(ssid, bssid);
    }
    
    /**
     * Inner class để chứa WiFi info
     */
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

