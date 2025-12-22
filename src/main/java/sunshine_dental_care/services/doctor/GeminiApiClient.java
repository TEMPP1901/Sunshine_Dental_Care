package sunshine_dental_care.services.doctor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Client for interacting with Google's Gemini AI API
 */
@Component("doctorGeminiApiClient")
@Slf4j
public class GeminiApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent}")
    private String apiUrl;

    @Value("${gemini.api.timeout:30000}")
    private int timeout;

    public GeminiApiClient(
            @Qualifier("geminiRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }


    /**
     * Generate content using Gemini AI API
     *
     * @param prompt The prompt/text to send to Gemini
     * @return Generated text response
     */
    public String generateContent(String prompt) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("Gemini API key is not configured");
            throw new IllegalStateException("Gemini API is not configured properly");
        }

        try {
            // Build request body
            Map<String, Object> requestBody = new HashMap<>();

            // Content parts
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", prompt);

            Map<String, Object> part = new HashMap<>();
            part.put("parts", Collections.singletonList(textPart));

            Map<String, Object> content = new HashMap<>();
            content.put("contents", Collections.singletonList(part));

            // Generation config
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.2); // Low temperature for more consistent results
            generationConfig.put("topK", 40);
            generationConfig.put("topP", 0.8);
            generationConfig.put("maxOutputTokens", 2048); // Limit response length

            requestBody.putAll(content);
            requestBody.put("generationConfig", generationConfig);
            requestBody.put("safetySettings", getSafetySettings());

            // Build URL with API key
            String url = UriComponentsBuilder.fromHttpUrl(apiUrl)
                    .queryParam("key", apiKey)
                    .toUriString();

            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            log.debug("Sending request to Gemini API. Prompt length: {}", prompt.length());

            // Send request
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                return extractGeneratedText(responseBody);
            } else {
                log.error("Gemini API returned error status: {}", response.getStatusCode());
                throw new RuntimeException("Gemini API returned status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            throw new RuntimeException("Failed to call Gemini API: " + e.getMessage(), e);
        }
    }

    /**
     * Extract generated text from Gemini API response
     */
    private String extractGeneratedText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Check for errors first
            if (root.has("error")) {
                String errorMessage = root.get("error").get("message").asText();
                log.error("Gemini API error: {}", errorMessage);
                throw new RuntimeException("Gemini API error: " + errorMessage);
            }

            // Extract text from candidates
            JsonNode candidates = root.get("candidates");
            if (candidates != null && candidates.isArray() && candidates.size() > 0) {
                JsonNode firstCandidate = candidates.get(0);
                JsonNode content = firstCandidate.get("content");

                if (content != null) {
                    JsonNode parts = content.get("parts");
                    if (parts != null && parts.isArray() && parts.size() > 0) {
                        String text = parts.get(0).get("text").asText();
                        log.debug("Successfully extracted generated text, length: {}", text.length());
                        return text;
                    }
                }

                // Check if candidate was blocked by safety filters
                JsonNode finishReason = firstCandidate.get("finishReason");
                if (finishReason != null && "SAFETY".equals(finishReason.asText())) {
                    log.warn("Gemini response blocked by safety filters");
                    return "Response blocked by content safety filters. Please adjust your prompt.";
                }
            }

            log.error("Unexpected Gemini API response structure: {}", responseBody);
            throw new RuntimeException("Unexpected response format from Gemini API");

        } catch (Exception e) {
            log.error("Failed to parse Gemini API response", e);
            throw new RuntimeException("Failed to parse Gemini API response", e);
        }
    }

    /**
     * Configure safety settings for medical context
     */
    private Map<String, Object>[] getSafetySettings() {
        return new Map[] {
                createSafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_MEDIUM_AND_ABOVE"),
                createSafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_MEDIUM_AND_ABOVE"),
                createSafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_MEDIUM_AND_ABOVE"),
                createSafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_MEDIUM_AND_ABOVE")
        };
    }

    private Map<String, Object> createSafetySetting(String category, String threshold) {
        Map<String, Object> setting = new HashMap<>();
        setting.put("category", category);
        setting.put("threshold", threshold);
        return setting;
    }

    /**
     * Simple validation of API configuration
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }



}