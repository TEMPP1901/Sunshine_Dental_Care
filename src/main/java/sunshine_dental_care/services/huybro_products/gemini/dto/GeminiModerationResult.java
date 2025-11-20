package sunshine_dental_care.services.huybro_products.gemini.dto;

import lombok.Data;

@Data
public class GeminiModerationResult {
    private boolean isSafe;
    private boolean isProductRelevant;
    private boolean needBetterImages;
    private String note;
}
