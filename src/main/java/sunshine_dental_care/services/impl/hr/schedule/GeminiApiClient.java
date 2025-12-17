package sunshine_dental_care.services.impl.hr.schedule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class GeminiApiClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public String generateContent(String prompt) {
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
            log.error("Gemini API key not configured.");
            throw new IllegalStateException("Gemini API key is not configured.");
        }

        List<String> availableModels = getAvailableGeminiModels();
        if (availableModels.isEmpty()) {
            log.warn("No available Gemini models found.");
            return null;
        }

        // thử từng model theo thứ tự ưu tiên (nếu model lỗi/quota exceeded thì thử tiếp model khác)
        for (String modelName : availableModels) {
            try {
                log.info("Trying Gemini model: {}", modelName);
                String apiUrl = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", modelName, geminiApiKey);
                String result = callApiWithModel(apiUrl, prompt);
                if (result != null) {
                    log.info("Successfully generated content with model: {}", modelName);
                    return result;
                }
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    log.warn("Model {} quota exceeded (429), trying next model...", modelName);
                } else {
                    log.warn("HTTP error with model {}: {} - {}", modelName, e.getStatusCode(), e.getResponseBodyAsString());
                }
            } catch (Exception e) {
                log.warn("Error with model {}: {}", modelName, e.getMessage());
            }
        }

        log.error("All available Gemini models failed.");
        return null;
    }

    private String callApiWithModel(String apiUrl, String prompt) {
        Map<String, Object> requestBody = buildRequestBody(prompt);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, String.class);
            String responseBody = response.getBody();

            if (responseBody == null) {
                log.error("Gemini API returned null response body");
                return null;
            }

            try {
                JsonNode rootNode = objectMapper.readTree(responseBody);
                JsonNode candidates = rootNode.path("candidates");

                if (candidates.isArray() && candidates.size() > 0) {
                    JsonNode firstCandidate = candidates.get(0);
                    JsonNode content = firstCandidate.path("content");
                    JsonNode parts = content.path("parts");

                    // lấy text ở phần content/parts đầu tiên
                    if (parts.isArray() && parts.size() > 0) {
                        JsonNode firstPart = parts.get(0);
                        String text = firstPart.path("text").asText();

                        if (text != null && !text.isEmpty()) {
                            log.debug("Extracted text from Gemini response (length: {})", text.length());
                            return text;
                        } else {
                            log.warn("Gemini response has empty text in parts[0].text");
                        }
                    } else {
                        log.warn("Gemini response has no parts in content");
                    }
                } else {
                    log.warn("Gemini response has no candidates or empty candidates array");
                }

                // Nếu parse không ra thì trả về raw response string
                log.warn("Failed to parse Gemini response format, returning raw response");
                return responseBody;

            } catch (JsonProcessingException e) {
                log.error("Failed to parse Gemini API response as JSON: {}", e.getMessage());
                log.debug("Raw response: {}", responseBody);
                return responseBody;
            }

        } catch (ResourceAccessException e) {
            log.error("Network error calling Gemini API: {}", e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> buildRequestBody(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();

        // build content cho request
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(part));
        requestBody.put("contents", List.of(content));

        // cấu hình gen (khuyến khích trả về JSON output)
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.0);
        generationConfig.put("responseMimeType", "application/json");
        requestBody.put("generationConfig", generationConfig);

        return requestBody;
    }

    private List<String> getAvailableGeminiModels() {
        String listModelsUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=" + geminiApiKey;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(listModelsUrl, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            String[] preferredModels = {"gemini-1.5-flash-latest", "gemini-1.5-pro-latest", "gemini-pro"};
            List<String> supportedModels = new ArrayList<>();

            // lấy danh sách model hỗ trợ generateContent
            StreamSupport.stream(root.path("models").spliterator(), false)
                .filter(model -> model.path("name").asText().contains("gemini"))
                .filter(model -> !model.path("name").asText().contains("embedding"))
                .forEach(model -> {
                    boolean supportsGenerateContent = StreamSupport.stream(model.path("supportedGenerationMethods").spliterator(), false)
                        .anyMatch(method -> "generateContent".equals(method.asText()));
                    if (supportsGenerateContent) {
                        supportedModels.add(model.path("name").asText().replace("models/", ""));
                    }
                });

            // ưu tiên thử theo thứ tự preferredModels trước
            List<String> orderedModels = new ArrayList<>();
            for (String preferred : preferredModels) {
                if (supportedModels.contains(preferred)) {
                    orderedModels.add(preferred);
                }
            }
            supportedModels.removeAll(orderedModels);
            orderedModels.addAll(supportedModels);

            log.info("Found {} compatible models. Order: {}", orderedModels.size(), orderedModels);
            return orderedModels;

        } catch (HttpClientErrorException | JsonProcessingException e) {
            log.warn("Failed to list available Gemini models: {}", e.getMessage());
            // fallback về model default nếu gọi list lỗi
            return List.of("gemini-1.5-flash-latest");
        }
    }
}
