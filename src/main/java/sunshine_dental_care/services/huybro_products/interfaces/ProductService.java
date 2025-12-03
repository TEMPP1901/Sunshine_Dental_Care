package sunshine_dental_care.services.huybro_products.interfaces;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import sunshine_dental_care.dto.huybro_products.ProductCreateDto;
import sunshine_dental_care.dto.huybro_products.ProductDto;
import sunshine_dental_care.dto.huybro_products.ProductFilterDto;
import sunshine_dental_care.dto.huybro_products.ProductUpdateDto;

public interface ProductService {
    List<ProductDto> findAllProducts();
    List<ProductDto> findAllProductsForAccountant();
    ProductDto findById(Integer id);
    Page<ProductDto> search(ProductFilterDto filter, Pageable pageable);
    ProductDto createProduct(ProductCreateDto dto);
    ProductDto updateProduct(Integer id, ProductUpdateDto dto);
    String suggestSku(List<String> typeNames);
}
