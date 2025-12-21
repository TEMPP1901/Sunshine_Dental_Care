package sunshine_dental_care.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "openrouter.api")
public class OpenRouterProperties {
    private String apiKey;
    private String model = "meta-llama/llama-3.1-8b-instruct";
}