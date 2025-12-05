package sunshine_dental_care.services.huybro_products.gemini.dto;

import lombok.Data;

import java.util.List;

@Data
public class GeminiVisionResult {
    private String productName;
    private String brand;
    private String productDescription;
    private List<String> typeNames;
    private boolean needBetterImages;
    private String note;
    private List<GeminiModerationImageResult> images;
}