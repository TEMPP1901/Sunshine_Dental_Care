package sunshine_dental_care.services.huybro_products.gemini.services.product_image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sunshine_dental_care.services.huybro_products.gemini.dto.GeminiModerationResult;
import sunshine_dental_care.services.huybro_products.gemini.dto.GeminiVisionResult;
import sunshine_dental_care.services.huybro_products.gemini.services.client.GeminiApiClient;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiProductImageFlowService {

    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper;

    public GeminiModerationResult runModerationOnly(String moderationRequestJson) {
        log.info("[GeminiFlow] Bắt đầu luồng validation-only: moderation (kiểm tra 1–3 ảnh).");

        String moderationResponse = geminiApiClient.callModeration(moderationRequestJson);

        GeminiModerationResult moderationResult = parseModerationResponse(moderationResponse);

        if (!moderationResult.isSafe()) {
            log.warn("[GeminiFlow] Moderation: image not safe, note={}", moderationResult.getNote());
            throw new IllegalArgumentException(
                    moderationResult.getNote() != null ? moderationResult.getNote() : "Images are not safe"
            );
        }

        if (!moderationResult.isProductRelevant()) {
            log.warn("[GeminiFlow] Moderation: image not product relevant, note={}", moderationResult.getNote());
            throw new IllegalArgumentException(
                    moderationResult.getNote() != null ? moderationResult.getNote() : "Images are not relevant to dental products"
            );
        }

        if (moderationResult.isNeedBetterImages()) {
            log.warn("[GeminiFlow] Moderation: need better images, note={}", moderationResult.getNote());
            throw new IllegalArgumentException(
                    moderationResult.getNote() != null ? moderationResult.getNote() : "Images are too blurry or low quality"
            );
        }

        log.info("[GeminiFlow] Validation-only: moderation passed.");

        return moderationResult;
    }

    public GeminiVisionResult runFlowModerationThenVision(String moderationRequestJson, String visionRequestJson) {
        log.info("[GeminiFlow] Bắt đầu luồng 1: moderation (kiểm tra 1–3 ảnh).");

        String moderationResponse = geminiApiClient.callModeration(moderationRequestJson);

        GeminiModerationResult moderationResult = parseModerationResponse(moderationResponse);

        if (!moderationResult.isSafe()) {
            log.warn("[GeminiFlow] Moderation: image not safe, note={}", moderationResult.getNote());
            throw new IllegalArgumentException(
                    moderationResult.getNote() != null ? moderationResult.getNote() : "Images are not safe"
            );
        }

        if (!moderationResult.isProductRelevant()) {
            log.warn("[GeminiFlow] Moderation: image not product relevant, note={}", moderationResult.getNote());
            throw new IllegalArgumentException(
                    moderationResult.getNote() != null ? moderationResult.getNote() : "Images are not relevant to dental products"
            );
        }

        if (moderationResult.isNeedBetterImages()) {
            log.warn("[GeminiFlow] Moderation: need better images, note={}", moderationResult.getNote());
            throw new IllegalArgumentException(
                    moderationResult.getNote() != null ? moderationResult.getNote() : "Images are too blurry or low quality"
            );
        }

        log.info("[GeminiFlow] Luồng 1 đã chạy xong -> chuyển sang luồng 2: vision (phân tích sản phẩm).");

        String visionResponse = geminiApiClient.callVision(visionRequestJson);

        log.info("[GeminiFlow] Luồng 2 đã chạy xong. Hoàn thành pipeline moderation -> vision.");

        GeminiVisionResult visionResult = parseVisionResponse(visionResponse);

        if (visionResult.isNeedBetterImages()) {
            log.warn("[GeminiFlow] Vision: need better images, note={}", visionResult.getNote());
        } else {
            log.info("[GeminiFlow] Vision OK: productName='{}', brand='{}', typeNames={}",
                    visionResult.getProductName(),
                    visionResult.getBrand(),
                    visionResult.getTypeNames()
            );
        }

        return visionResult;
    }

    private GeminiModerationResult parseModerationResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new IllegalStateException("No candidates in moderation response");
            }

            JsonNode content = candidates.get(0).path("content");
            JsonNode parts = content.path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new IllegalStateException("No parts in moderation response");
            }

            String jsonText = parts.get(0).path("text").asText();
            JsonNode resultNode = objectMapper.readTree(jsonText);

            GeminiModerationResult result = new GeminiModerationResult();
            result.setSafe(resultNode.path("isSafe").asBoolean(false));
            result.setProductRelevant(resultNode.path("isProductRelevant").asBoolean(false));
            result.setNeedBetterImages(resultNode.path("needBetterImages").asBoolean(false));
            result.setNote(resultNode.path("note").asText(null));

            return result;
        } catch (Exception e) {
            log.error("[GeminiFlow] Cannot parse moderation response JSON", e);
            throw new IllegalStateException("Cannot parse moderation response JSON", e);
        }
    }

    private GeminiVisionResult parseVisionResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new IllegalStateException("No candidates in vision response");
            }

            JsonNode content = candidates.get(0).path("content");
            JsonNode parts = content.path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new IllegalStateException("No parts in vision response");
            }

            String jsonText = parts.get(0).path("text").asText();
            JsonNode resultNode = objectMapper.readTree(jsonText);

            GeminiVisionResult result = new GeminiVisionResult();
            result.setProductName(trimToNull(resultNode.path("productName").asText(null)));
            result.setBrand(trimToNull(resultNode.path("brand").asText(null)));
            result.setProductDescription(trimToNull(resultNode.path("productDescription").asText(null)));
            result.setNeedBetterImages(resultNode.path("needBetterImages").asBoolean(false));
            result.setNote(resultNode.path("note").asText(null));

            List<String> typeNames = new ArrayList<>();
            JsonNode typeArray = resultNode.path("typeNames");
            if (typeArray.isArray()) {
                for (JsonNode t : typeArray) {
                    String v = trimToNull(t.asText(null));
                    if (v != null) {
                        typeNames.add(v);
                    }
                }
            }
            result.setTypeNames(typeNames);

            return result;
        } catch (Exception e) {
            log.error("[GeminiFlow] Cannot parse vision response JSON", e);
            throw new IllegalStateException("Cannot parse vision response JSON", e);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }
}
