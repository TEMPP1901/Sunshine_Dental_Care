package sunshine_dental_care.services.huybro_products.gemini.services.product_image.moderation_vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import sunshine_dental_care.entities.huybro_products.ProductType;
import sunshine_dental_care.repositories.huybro_products.ProductTypeRepository;
import sunshine_dental_care.services.huybro_products.gemini.dto.*;
import sunshine_dental_care.services.huybro_products.gemini.services.product_image.GeminiProductImageFlowService;
import sunshine_dental_care.utils.huybro_utils.format.FormatTypeProduct;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiProductImageServiceImpl implements GeminiProductImageService {

    private final ObjectMapper objectMapper;
    private final GeminiProductImageFlowService flowService;
    private final ProductTypeRepository productTypeRepository;


    @Value("classpath:gemini/prompts/moderation_prompt.json")
    private Resource moderationPromptResource;

    @Value("classpath:gemini/prompts/vision_product_prompt.json")
    private Resource visionPromptResource;

    @Override
    public GeminiVisionResult analyzeProductImages(ProductImageAnalyzeRequestDto request) {

        List<ProductImageBase64Dto> images = request.getImages();
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("Product images must not be empty");
        }

        List<ProductImageBase64Dto> sorted = images.stream()
                .sorted(Comparator.comparing(ProductImageBase64Dto::getImageOrder))
                .toList();

        List<String> base64Images = sorted.stream()
                .map(ProductImageBase64Dto::getBase64)
                .toList();

        List<String> allowedTypeNames = productTypeRepository.findAll()
                .stream()
                .map(ProductType::getTypeName)
                .toList();

        String moderationRequestJson = buildModerationRequestJson(base64Images);
        String visionRequestJson = buildVisionRequestJson(base64Images, allowedTypeNames);

        GeminiVisionResult result = flowService.runFlowModerationThenVision(
                moderationRequestJson,
                visionRequestJson
        );

        List<GeminiModerationImageResult> moderationImages = result.getImages();
        if (moderationImages == null || moderationImages.isEmpty()) {
            throw new IllegalStateException("No moderation results found");
        }

        List<Integer> invalidOrders = new ArrayList<>();

        for (var res : moderationImages) {
            if (!res.isSafe() || !res.isProductRelevant()) {

                int idx = res.getIndex();
                if (idx >= 0 && idx < sorted.size()) {
                    invalidOrders.add(sorted.get(idx).getImageOrder());
                } else {
                    invalidOrders.add(idx + 1);
                }
            }
        }

        int total = sorted.size();
        int invalidCount = invalidOrders.size();
        int validCount = total - invalidCount;

        if (validCount == 0) {
            throw new IllegalArgumentException("All images are invalid. Cannot generate product content.");
        }

        if (!invalidOrders.isEmpty()) {
            log.warn("[GeminiProductImageService] Invalid images at positions: {}", invalidOrders);
        }

        // Resolve typeNames theo logic BE
        List<String> resolvedTypeNames = FormatTypeProduct.resolveTypeNames(
                result.getProductName(),
                result.getProductDescription(),
                allowedTypeNames,
                3
        );

        result.setTypeNames(resolvedTypeNames);

        return result;
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

    private String buildVisionRequestJson(
            List<String> base64Images,
            List<String> allowedTypeNames
    ) {
        ObjectNode root = objectMapper.createObjectNode();

        String systemJsonPrompt = readPromptResource(visionPromptResource);

        ObjectNode systemInstruction = root.putObject("systemInstruction");
        systemInstruction.put("role", "user");
        ArrayNode sysParts = systemInstruction.putArray("parts");
        sysParts.addObject().put("text", systemJsonPrompt);


        ObjectNode userContent = objectMapper.createObjectNode();
        userContent.put("role", "user");
        ArrayNode userParts = userContent.putArray("parts");

        String allowedTypesText = (allowedTypeNames != null && !allowedTypeNames.isEmpty())
                ? "allowedTypeNames: " + String.join(", ", allowedTypeNames)
                : "allowedTypeNames: []";

        userParts.addObject().put("text",
                "Analyze these images of the same product. " +
                        "Use the JSON response_format. " +
                        allowedTypesText +
                        ". Return JSON only."
        );

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
            throw new IllegalStateException("Cannot build vision request JSON", e);
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
