package sunshine_dental_care.config.huybro_config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GoogConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
