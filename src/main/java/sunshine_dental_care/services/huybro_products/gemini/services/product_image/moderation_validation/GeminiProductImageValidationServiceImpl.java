package sunshine_dental_care.services.huybro_products.gemini.services.product_image.moderation_validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import sunshine_dental_care.services.huybro_products.gemini.dto.GeminiModerationResult;
import sunshine_dental_care.services.huybro_products.gemini.dto.ProductImageBase64Dto;
import sunshine_dental_care.services.huybro_products.gemini.dto.ProductImageValidateRequestDto;
import sunshine_dental_care.services.huybro_products.gemini.services.product_image.GeminiProductImageFlowService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GeminiProductImageValidationServiceImpl implements GeminiProductImageValidationService {

    private final ObjectMapper objectMapper;
    private final GeminiProductImageFlowService flowService;

    @Value("classpath:gemini/prompts/moderation_prompt.json")
    private Resource moderationPromptResource;

    @Override
    public void validateProductImages(ProductImageValidateRequestDto request) {
        List<ProductImageBase64Dto> images = request.getImages();

        if (images == null || images.size() != 3) {
            throw new IllegalArgumentException("Product must contain exactly 3 images");
        }

        List<ProductImageBase64Dto> sorted = images.stream()
                .sorted(Comparator.comparing(ProductImageBase64Dto::getImageOrder))
                .toList();

        List<String> base64Images = sorted.stream()
                .map(ProductImageBase64Dto::getBase64)
                .toList();

        String moderationRequestJson = buildModerationRequestJson(base64Images);

        GeminiModerationResult result = flowService.runModerationOnly(moderationRequestJson);

        List<ProductImageBase64Dto> sortedImages = images.stream()
                .sorted(Comparator.comparing(ProductImageBase64Dto::getImageOrder))
                .toList();

        List<String> errorMessages = new ArrayList<>();

        for (var imgRes : result.getImages()) {
            if (!imgRes.isSafe() || !imgRes.isProductRelevant() ||
                    (imgRes.getQuality() != null && !"OK".equalsIgnoreCase(imgRes.getQuality()))) {

                int idx = imgRes.getIndex();
                int imageOrder;
                if (idx >= 0 && idx < sortedImages.size()) {
                    imageOrder = sortedImages.get(idx).getImageOrder(); // 1,2,3
                } else {
                    imageOrder = idx + 1; // fallback
                }

                List<String> reasons = new ArrayList<>();

                if (!imgRes.isSafe()) {
                    String r = imgRes.getSafetyReason();
                    reasons.add("unsafe" + (r != null ? " (" + r + ")" : ""));
                }

                if (!imgRes.isProductRelevant()) {
                    String r = imgRes.getRelevanceReason();
                    reasons.add("not relevant" + (r != null ? " (" + r + ")" : ""));
                }

                if (imgRes.getQuality() != null &&
                        !"OK".equalsIgnoreCase(imgRes.getQuality())) {
                    String r = imgRes.getQualityReason();
                    reasons.add("quality=" + imgRes.getQuality() + (r != null ? " (" + r + ")" : ""));
                }

                String reasonText = reasons.isEmpty()
                        ? "invalid"
                        : String.join("; ", reasons);

                errorMessages.add("Image " + imageOrder + ": " + reasonText);
            }
        }

        if (!errorMessages.isEmpty()) {
            String message = String.join(" | ", errorMessages);
            throw new IllegalArgumentException(message);
        }


    }

    private String buildModerationRequestJson(List<String> base64Images) {
        ObjectNode root = objectMapper.createObjectNode();

        String systemJsonPrompt = readPromptResource(moderationPromptResource);

        ObjectNode systemInstruction = root.putObject("systemInstruction");
        systemInstruction.put("role", "user"); // role hợp lệ
        ArrayNode sysParts = systemInstruction.putArray("parts");
        sysParts.addObject().put("text", systemJsonPrompt);

        ObjectNode userContent = objectMapper.createObjectNode();
        userContent.put("role", "user");
        ArrayNode userParts = userContent.putArray("parts");
        userParts.addObject().put("text", "Analyze these product images and return JSON only.");

        if (base64Images != null) {
            for (String base64 : base64Images) {
                ObjectNode imagePart = userParts.addObject();
                ObjectNode inlineData = imagePart.putObject("inline_data");
                inlineData.put("mime_type", "image/jpeg");
                inlineData.put("data", base64);
            }
        }

        ArrayNode contents = root.putArray("contents");
        contents.add(userContent);

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build moderation request JSON", e);
        }
    }

    private String readPromptResource(Resource resource) {
        try {
            byte[] bytes = resource.getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read prompt resource: " + resource, e);
        }
    }
}
