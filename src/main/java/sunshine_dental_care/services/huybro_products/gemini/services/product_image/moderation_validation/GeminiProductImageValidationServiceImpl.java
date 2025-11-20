package sunshine_dental_care.services.huybro_products.gemini.services.product_image.moderation_validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import sunshine_dental_care.services.huybro_products.gemini.dto.ProductImageValidateRequestDto;
import sunshine_dental_care.services.huybro_products.gemini.services.product_image.GeminiProductImageFlowService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GeminiProductImageValidationServiceImpl implements GeminiProductImageValidationService {

    private final ObjectMapper objectMapper;
    private final GeminiProductImageFlowService flowService;

    @Value("classpath:gemini/prompts/moderation_prompt.json")
    private Resource moderationPromptResource;

    @Override
    public void validateProductImages(ProductImageValidateRequestDto request) {
        List<String> base64Images = request.getBase64Images();

        String moderationRequestJson = buildModerationRequestJson(base64Images);

        flowService.runModerationOnly(moderationRequestJson);
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
