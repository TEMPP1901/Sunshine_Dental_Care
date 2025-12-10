package sunshine_dental_care.services.huybro_products.gemini.services.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import sunshine_dental_care.services.huybro_products.gemini.GeminiApiConfig;

@Service
@Slf4j
public class GeminiApiClientImpl implements GeminiApiClient {

    private final RestClient geminiRestClient;
    private final GeminiApiConfig geminiApiConfig;
    private final ObjectMapper objectMapper;

    public GeminiApiClientImpl(
            RestClient geminiRestClient,
            GeminiApiConfig geminiApiConfig,
            ObjectMapper objectMapper
    ) {
        this.geminiRestClient = geminiRestClient;
        this.geminiApiConfig = geminiApiConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public String callModeration(String requestJson) {
        String model = geminiApiConfig.getModerationModel();
        String tag = "[Gemini][Moderation]";

        log.info("{} Bắt đầu gọi model={}, chuẩn bị gửi request lên Gemini...", tag, model);

        try {
            String response = geminiRestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/" + geminiApiConfig.getModerationModel() + ":generateContent")
                            .queryParam("key", geminiApiConfig.getApiKey())
                            .build())
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);

            log.info("{} Đã kết nối và nhận response từ Gemini thành công.", tag);
            logUsage(tag, response);
            return response;
        } catch (RestClientResponseException e) {
            handleRestClientResponseException(tag, e);
            throw e;
        } catch (RestClientException e) {
            log.error("{} Kết nối tới Gemini thất bại: {}", tag, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public String callVision(String requestJson) {
        String model = geminiApiConfig.getVisionModel();
        String tag = "[Gemini][Vision]";

        log.info("{} Bắt đầu gọi model={}, chuẩn bị gửi request lên Gemini...", tag, model);

        try {
            String response = geminiRestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/" + geminiApiConfig.getVisionModel() + ":generateContent")
                            .queryParam("key", geminiApiConfig.getApiKey())
                            .build())
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);

            log.info("{} Đã kết nối và nhận response từ Gemini thành công.", tag);
            logUsage(tag, response);
            return response;
        } catch (RestClientResponseException e) {
            handleRestClientResponseException(tag, e);
            throw e;
        } catch (RestClientException e) {
            log.error("{} Kết nối tới Gemini thất bại: {}", tag, e.getMessage(), e);
            throw e;
        }
    }

    private void logUsage(String tag, String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode usage = root.path("usageMetadata");

            if (!usage.isMissingNode()) {
                long promptTokens = usage.path("promptTokenCount").asLong(-1);
                long candidatesTokens = usage.path("candidatesTokenCount").asLong(-1);
                long totalTokens = usage.path("totalTokenCount").asLong(-1);

                log.info(
                        "{} Token usage -> Lượng token gửi đi (prompt) = {}, " +
                                "Lượng token nhận vào (candidates) = {}, Tổng token = {}",
                        tag,
                        promptTokens,
                        candidatesTokens,
                        totalTokens
                );
            } else {
                log.warn("{} Response không có usageMetadata -> không đọc được token usage.", tag);
            }
        } catch (Exception ex) {
            log.warn("{} Không parse được token usage từ response Gemini: {}", tag, ex.getMessage());
        }
    }

    private void handleRestClientResponseException(String tag, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String body = e.getResponseBodyAsString();

        if (status == 429 || body.contains("RESOURCE_EXHAUSTED")) {
            log.warn(
                    "{} Hết token/quota gọi Gemini. Status={}, body={}, " +
                            "Hết token vui lòng đợi một thời gian trước khi thử lại.",
                    tag,
                    status,
                    body
            );
        } else {
            log.error(
                    "{} Gọi Gemini thất bại. Status={}, body={}",
                    tag,
                    status,
                    body
            );
        }
    }
}
