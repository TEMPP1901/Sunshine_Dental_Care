package sunshine_dental_care.services.huybro_products.gemini.dto;

import lombok.Data;

@Data
public class GeminiModerationImageResult {
    private int index;
    private boolean safe;
    private boolean productRelevant;
    private String quality;
    private String safetyReason;
    private String relevanceReason;
    private String qualityReason;
}
