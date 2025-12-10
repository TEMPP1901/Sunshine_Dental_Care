package sunshine_dental_care.services.huybro_products.gemini.services.product_image.moderation_validation;

import sunshine_dental_care.services.huybro_products.gemini.dto.ProductImageValidateRequestDto;

public interface GeminiProductImageValidationService {

    void validateProductImages(ProductImageValidateRequestDto request);
}