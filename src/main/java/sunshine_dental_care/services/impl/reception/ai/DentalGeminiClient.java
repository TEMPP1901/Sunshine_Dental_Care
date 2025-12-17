package sunshine_dental_care.services.impl.reception.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DentalGeminiClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${dental.gemini.api-key}")
    private String apiKey;

    private static final String MODEL_ID = "gemini-2.5-flash";

    // URL chuẩn cho v1beta
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_ID + ":generateContent";

    public String generateContent(String prompt) {
        // Cơ chế thử lại 3 lần nếu server 2.5 quá tải (Lỗi 503)
        int maxRetries = 3;

        for (int i = 0; i < maxRetries; i++) {
            try {
                return callApi(prompt);
            } catch (HttpServerErrorException.ServiceUnavailable e) {
                log.warn("⚠️ Model {} đang bận (503), đang thử lại lần {}...", MODEL_ID, i + 1);
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {} // Chờ 2s rồi gọi lại
            } catch (Exception e) {
                log.error("🔥 Lỗi không thể hồi phục: {}", e.getMessage());
                return "{\"type\": \"CHAT\", \"content\": \"Lỗi: " + e.getMessage() + "\"}";
            }
        }

        return "{\"type\": \"CHAT\", \"content\": \"Server AI đang quá tải, vui lòng thử lại sau.\"}";
    }

    private String callApi(String prompt) {
        if (apiKey == null || apiKey.isEmpty()) return "Chưa có API Key";

        // Body JSON
        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        // Header (Dùng x-goog-api-key cho chuẩn)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // Gọi API
        ResponseEntity<String> response = restTemplate.postForEntity(API_URL, entity, String.class);

        try {
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            if (rootNode.path("candidates").isEmpty()) return "AI không trả lời.";
            return rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        } catch (Exception e) {
            return "Lỗi parse JSON: " + e.getMessage();
        }
    }
}