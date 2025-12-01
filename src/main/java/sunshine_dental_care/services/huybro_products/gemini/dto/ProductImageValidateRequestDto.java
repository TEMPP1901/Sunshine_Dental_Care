package sunshine_dental_care.services.huybro_products.gemini.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProductImageValidateRequestDto {
    private List<ProductImageBase64Dto> images;
}