package sunshine_dental_care.services.auth_service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class SmsService {

    @Value("${ESMS_API_KEY}")
    private String apiKey;

    @Value("${ESMS_SECRET_KEY}")
    private String secretKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void sendOtp(String phone, String otp) {
        try {
            // [QUAN TRỌNG 1] Nội dung phải đúng mẫu test của Baotrixemay
            // Tài liệu trang 8: "{OTP} la ma xac minh dang ky Baotrixemay cua ban"
            String content = otp + " la ma xac minh dang nhap Dental Clinic cua ban";

            String encodedContent = URLEncoder.encode(content, StandardCharsets.UTF_8);
            String cleanPhone = phone.trim();

            // [QUAN TRỌNG 2] Cấu hình chuẩn cho tài khoản Test theo tài liệu
            // Brandname: "Baotrixemay" (Bắt buộc)
            // SmsType: 2 (Tin nhắn CSKH)
            String url = String.format(
                    "http://rest.esms.vn/MainService.svc/json/SendMultipleMessage_V4_get?Phone=%s&Content=%s&ApiKey=%s&SecretKey=%s&Brandname=Baotrixemay&SmsType=2",
                    cleanPhone, encodedContent, apiKey, secretKey
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("eSMS Response: {}", response.body());

            System.out.println(">> [eSMS REQUEST] " + url);
            System.out.println(">> [OTP SENT] Phone: " + cleanPhone + " | OTP: " + otp);

        } catch (Exception e) {
            log.error("Lỗi gửi eSMS: {}", e.getMessage());
            System.out.println(">> [FALLBACK] Phone: " + phone + " | OTP: " + otp);
        }
    }
}