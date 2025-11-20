package sunshine_dental_care.services.huybro_products.gemini.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProductImageAnalyzeRequestDto {
    private List<String> base64Images;
    private List<String> allowedTypeNames;
}
