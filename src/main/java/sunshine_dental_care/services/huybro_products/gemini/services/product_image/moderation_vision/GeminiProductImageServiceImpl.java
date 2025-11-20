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
import sunshine_dental_care.services.huybro_products.gemini.dto.GeminiVisionResult;
import sunshine_dental_care.services.huybro_products.gemini.dto.ProductImageAnalyzeRequestDto;
import sunshine_dental_care.services.huybro_products.gemini.services.product_image.GeminiProductImageFlowService;
import sunshine_dental_care.utils.huybro_utils.format.FormatTypeProduct;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
        var base64Images = request.getBase64Images();

        List<String> allowedTypeNames = productTypeRepository.findAll()
                .stream()
                .map(ProductType::getTypeName)
                .toList();

        String moderationRequestJson = buildModerationRequestJson(base64Images);
        String visionRequestJson = buildVisionRequestJson(base64Images, allowedTypeNames);

        GeminiVisionResult visionResult = flowService.runFlowModerationThenVision(
                moderationRequestJson,
                visionRequestJson
        );

        List<String> resolvedTypeNames = FormatTypeProduct.resolveTypeNames(
                visionResult.getProductName(),
                visionResult.getProductDescription(),
                allowedTypeNames,
                3
        );

        visionResult.setTypeNames(resolvedTypeNames);

        return visionResult;
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
