package sunshine_dental_care.services.huybro_products.gemini.dto;

import lombok.Data;

import java.util.List;

@Data
public class GeminiModerationResult {
    private List<GeminiModerationImageResult> images;
    private boolean needBetterImages;
    private String note;
}
