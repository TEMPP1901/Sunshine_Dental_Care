package sunshine_dental_care;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Bật Cron Job => tự động quét và hủy lịch nếu chưa thanh toán cọc sau xx phút
@EnableAsync
public class SunshineDentalCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(SunshineDentalCareApplication.class, args);
    }

}
