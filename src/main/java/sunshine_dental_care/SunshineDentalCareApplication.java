package sunshine_dental_care;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableAsync
public class SunshineDentalCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(SunshineDentalCareApplication.class, args);
    }

}
