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

// Lớp cấu hình để đọc cài đặt WiFi từ application.properties
// Hỗ trợ cả cấu hình toàn cục và cấu hình riêng từng cơ sở (clinic)
@Configuration
@ConfigurationProperties(prefix = "app.attendance.wifi")
@Getter
@Setter
public class WiFiConfig {

    // true = bắt buộc kiểm tra (nếu sai sẽ throw exception)
    // false = chỉ ghi log cảnh báo, không chặn check-in
    private boolean enforce = true;

    // Danh sách SSID cho phép, cách nhau bằng dấu phẩy (dùng toàn cục, fallback)
    private String allowedSsids = "";

    // Danh sách BSSID cho phép, cách nhau bằng dấu phẩy (dùng toàn cục, fallback)
    private String allowedBssids = "";

    // Cấu hình WiFi riêng cho từng clinic
    private Map<String, ClinicWiFiConfig> clinic = new HashMap<>();

    @Getter
    @Setter
    public static class ClinicWiFiConfig {
        // Danh sách SSID riêng của clinic đó (cách nhau bằng dấu phẩy)
        private String ssids = "";
        // Danh sách BSSID riêng của clinic đó (cách nhau bằng dấu phẩy)
        private String bssids = "";
    }

    // Lấy danh sách SSID hợp lệ từ config toàn cục
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

    // Lấy danh sách BSSID hợp lệ từ config toàn cục
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

    // Kiểm tra SSID có nằm trong whitelist cho phép không
    public boolean isSsidAllowed(String ssid) {
        if (ssid == null || ssid.trim().isEmpty()) {
            return false;
        }
        List<String> allowedList = getAllowedSsidsList();
        // Nếu không có whitelist thì không cho phép (phải cấu hình rõ ràng)
        if (allowedList.isEmpty()) {
            return false;
        }
        return allowedList.contains(ssid.trim().toUpperCase());
    }

    // Kiểm tra BSSID có nằm trong whitelist cho phép không
    public boolean isBssidAllowed(String bssid) {
        if (bssid == null || bssid.trim().isEmpty()) {
            return false;
        }
        List<String> allowedList = getAllowedBssidsList();
        // Nếu không có whitelist thì không cho phép (phải cấu hình rõ ràng)
        if (allowedList.isEmpty()) {
            return false;
        }
        return allowedList.contains(bssid.trim().toUpperCase());
    }

    // Kiểm tra WiFi hợp lệ dùng cho check-in: đúng ít nhất 1 trong 2 (SSID hoặc BSSID)
    public boolean isWiFiAllowed(String ssid, String bssid) {
        return isSsidAllowed(ssid) || isBssidAllowed(bssid);
    }

    // Kiểm tra WiFi theo từng clinic (ưu tiên config riêng của clinic, nếu k có dùng config toàn cục)
    public boolean isWiFiAllowedForClinic(String ssid, String bssid, Integer clinicId) {
        if (clinicId == null) {
            // Không truyền vào id clinic - sử dụng config toàn cục
            return isWiFiAllowed(ssid, bssid);
        }

        // Nếu có cấu hình riêng cho clinic thì kiểm tra theo clinic đó
        ClinicWiFiConfig clinicConfig = clinic.get(String.valueOf(clinicId));
        if (clinicConfig != null) {
            List<String> clinicAllowedSsids = parseList(clinicConfig.getSsids());
            List<String> clinicAllowedBssids = parseList(clinicConfig.getBssids());

            boolean ssidMatch = ssid != null && !ssid.trim().isEmpty()
                && clinicAllowedSsids.contains(ssid.trim().toUpperCase());
            boolean bssidMatch = bssid != null && !bssid.trim().isEmpty()
                && clinicAllowedBssids.contains(bssid.trim().toUpperCase());

            return ssidMatch || bssidMatch;
        }

        // Không có cấu hình riêng thì fallback sang cấu hình toàn cục
        return isWiFiAllowed(ssid, bssid);
    }

    // Chuyển chuỗi các giá trị cách nhau bởi dấu phẩy thành List<String>, loại bỏ khoảng trắng, in hoa
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
