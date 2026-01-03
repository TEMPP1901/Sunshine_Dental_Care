package sunshine_dental_care.services.huybro_products.gemini.services.product_image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sunshine_dental_care.services.huybro_products.gemini.dto.GeminiModerationImageResult; // NEW
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

    /**
     * Dùng cho nút Create / Update:
     *  - YÊU CẦU: cả 3/3 ảnh phải safe + productRelevant
     *  - Nếu ảnh nào fail => throw
     */
    public GeminiModerationResult runModerationOnly(String moderationRequestJson) {
        log.info("[GeminiFlow] Bắt đầu luồng validation-only: moderation (kiểm tra 1–3 ảnh).");

        String moderationResponse = geminiApiClient.callModeration(moderationRequestJson);

        GeminiModerationResult moderationResult = parseModerationResponse(moderationResponse);

        List<GeminiModerationImageResult> images = moderationResult.getImages();
        if (images == null || images.isEmpty()) {
            throw new IllegalStateException("No image results in moderation response");
        }

        long validCount = images.stream()
                .filter(img -> img.isSafe() && img.isProductRelevant())
                .count();

        boolean allValid = (validCount == images.size());

        if (allValid && !moderationResult.isNeedBetterImages()) {
            log.info("[GeminiFlow] Validation-only: moderation passed ({} / {} images valid).",
                    validCount, images.size());
        } else {
            log.warn("[GeminiFlow] Validation-only: some images may be invalid or low quality. " +
                            "validCount={}, total={}, needBetterImages={}",
                    validCount, images.size(), moderationResult.isNeedBetterImages());
        }

        return moderationResult;
    }

    /**
     * Dùng cho Gen text từ ảnh:
     *  - YÊU CẦU: CHỈ CẦN >= 1 ảnh safe + relevant
     *  - Nếu 0 ảnh hợp lệ => throw, không gen
     */
    public GeminiVisionResult runFlowModerationThenVision(String moderationRequestJson, String visionRequestJson) {
        log.info("[GeminiFlow] Bắt đầu luồng 1: moderation (kiểm tra 1–3 ảnh).");

        String moderationResponse = geminiApiClient.callModeration(moderationRequestJson);

        GeminiModerationResult moderationResult = parseModerationResponse(moderationResponse); // UPDATED

        List<GeminiModerationImageResult> images = moderationResult.getImages();
        if (images == null || images.isEmpty()) {
            throw new IllegalStateException("No image results in moderation response");
        }

        long validCount = images.stream()
                .filter(img -> img.isSafe() && img.isProductRelevant()) // UPDATED
                .count();

        if (validCount == 0) {
            List<String> errorDetails = new ArrayList<>();

            for (GeminiModerationImageResult img : images) {
                if (!img.isSafe() || !img.isProductRelevant()) {
                    // 1. Lấy lý do cụ thể do AI trả về (AI Log)
                    String reason = img.getSafetyReason();
                    if (reason == null || reason.isEmpty()) {
                        reason = img.getRelevanceReason();
                    }
                    if (reason == null || reason.isEmpty()) {
                        reason = "Unsafe or Irrelevant";
                    }
                    // 2. Format Index
                    int displayIndex = img.getIndex() + 1;

                    // 3. Ghép chuỗi
                    errorDetails.add("Image " + displayIndex + ": " + reason);
                }
            }

            String errorMsg = String.join(" | ", errorDetails);

            log.warn("[GeminiFlow] Moderation failed: {}", errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        if (moderationResult.isNeedBetterImages()) {
            log.warn("[GeminiFlow] Moderation: need better images, note={}", moderationResult.getNote());
            throw new IllegalArgumentException(
                    moderationResult.getNote() != null
                            ? moderationResult.getNote()
                            : "Images are too blurry or low quality"
            );
        }

        log.info("[GeminiFlow] Luồng 1 đã chạy xong ({} / {} images valid) -> chuyển sang luồng 2: vision.",
                validCount, images.size());

        String visionResponse = geminiApiClient.callVision(visionRequestJson);

        log.info("[GeminiFlow] Luồng 2 đã chạy xong. Hoàn thành pipeline moderation -> vision.");

        GeminiVisionResult visionResult = parseVisionResponse(visionResponse);

        visionResult.setImages(moderationResult.getImages());
        visionResult.setNeedBetterImages(
                visionResult.isNeedBetterImages() || moderationResult.isNeedBetterImages()
        );

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

    // ======================
    //  PARSE RESPONSES
    // ======================

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

            List<GeminiModerationImageResult> images = new ArrayList<>();
            JsonNode imageArray = resultNode.path("images");

            if (imageArray.isArray()) {
                for (JsonNode node : imageArray) {
                    GeminiModerationImageResult img = new GeminiModerationImageResult();
                    img.setIndex(node.path("index").asInt(0));
                    img.setSafe(node.path("isSafe").asBoolean(false));
                    img.setProductRelevant(node.path("isProductRelevant").asBoolean(false));
                    img.setQuality(node.path("quality").asText("OK")); // NEW
                    img.setSafetyReason(trimToNull(node.path("safetyReason").asText(null))); // NEW
                    img.setRelevanceReason(trimToNull(node.path("relevanceReason").asText(null))); // NEW
                    img.setQualityReason(trimToNull(node.path("qualityReason").asText(null))); // NEW

                    images.add(img);
                }
            }

            result.setImages(images);
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
