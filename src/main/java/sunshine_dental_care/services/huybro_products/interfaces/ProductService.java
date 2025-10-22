package sunshine_dental_care.services.huybro_products.interfaces;

import sunshine_dental_care.dto.huybro_products.ProductDto;
import java.util.List;

public interface ProductService {
    List<ProductDto> findAllProducts();
    ProductDto findById(Integer id);
}
