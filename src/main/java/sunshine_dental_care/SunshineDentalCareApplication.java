package sunshine_dental_care;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

//import để sử dụng Scheduler
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Bật Cron Job => tự động quét và hủy lịch nếu chưa thanh toán cọc sau xx phút
@org.springframework.scheduling.annotation.EnableAsync
@EnableScheduling
public class SunshineDentalCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(SunshineDentalCareApplication.class, args);
    }

}
