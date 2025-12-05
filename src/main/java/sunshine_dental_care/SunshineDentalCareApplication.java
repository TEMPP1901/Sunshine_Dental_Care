package sunshine_dental_care;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableAsync
@EnableScheduling
public class SunshineDentalCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(SunshineDentalCareApplication.class, args);
    }

}
