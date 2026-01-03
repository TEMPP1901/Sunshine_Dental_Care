package sunshine_dental_care.services.huybro_products.gemini;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@Configuration
@Data
@ConfigurationProperties(prefix = "gemini.api")
public class GeminiApiConfig {

    private String apiKey;
    private String endpoint;
    private String moderationModel;
    private String visionModel;

    @Bean
    public RestClient geminiRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(endpoint)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
