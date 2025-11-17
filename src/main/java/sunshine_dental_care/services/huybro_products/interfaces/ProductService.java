package sunshine_dental_care.services.huybro_products.interfaces;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import sunshine_dental_care.dto.huybro_products.ProductDto;
import sunshine_dental_care.dto.huybro_products.ProductFilterDto;

public interface ProductService {
    List<ProductDto> findAllProducts();
    ProductDto findById(Integer id);
    Page<ProductDto> search(ProductFilterDto filter, Pageable pageable);
}
