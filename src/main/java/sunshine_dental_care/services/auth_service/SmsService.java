package sunshine_dental_care.services.auth_service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service // <--- Quan trọng: Phải có dòng này để Spring nhận diện là Bean
@Slf4j
public class SmsService {

    public void sendOtp(String phone, String otp) {
        // --- MÔI TRƯỜNG DEV/LOCALHOST ---
        // In ra Console để bạn lấy mã OTP test
        System.out.println("========================================");
        System.out.println(">> SMS GỬI ĐẾN: " + phone);
        System.out.println(">> NỘI DUNG: Mã xác thực của bạn là: " + otp);
        System.out.println("========================================");

        // --- SAU NÀY TÍCH HỢP SMS THẬT THÌ CODE Ở ĐÂY ---
    }
}