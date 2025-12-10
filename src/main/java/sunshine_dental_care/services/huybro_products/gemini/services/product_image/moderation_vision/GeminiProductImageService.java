package sunshine_dental_care.services.huybro_products.gemini.services.product_image.moderation_vision;

import sunshine_dental_care.services.huybro_products.gemini.dto.GeminiVisionResult;
import sunshine_dental_care.services.huybro_products.gemini.dto.ProductImageAnalyzeRequestDto;

public interface GeminiProductImageService {

    GeminiVisionResult analyzeProductImages(ProductImageAnalyzeRequestDto request);
}
