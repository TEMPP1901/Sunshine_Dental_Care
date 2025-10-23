package sunshine_dental_care.api.huybro_products;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.config.huybro_config.enable.EnableTestOffSecurity;
import sunshine_dental_care.dto.huybro_products.ProductDto;
import sunshine_dental_care.services.huybro_products.interfaces.ProductService;
import sunshine_dental_care.utils.huybro_utils.image.ImageCacheUtil;

import java.io.File;
import java.util.List;

@EnableTestOffSecurity
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController (ProductService productService) {
        this.productService = productService;
    }

    // Hiển thị danh sách sản phẩm
    @GetMapping
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        List<ProductDto> products = productService.findAllProducts();
        if (products.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(products);
    }

    // Hiển thị chi tiết 1 sản phẩm theo id
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getProductById(@PathVariable Integer id) {
        ProductDto dto = productService.findById(id);
        if (dto == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(dto);
    }
    // Hiển thị hình ảnh đã resize
    @GetMapping("/images/{fileName}")
    public ResponseEntity<?> getResized(@PathVariable String fileName) {
        try {
            String originalDir = "uploads/products";
            String cacheDir = "uploads/cache/products";
            File cached = ImageCacheUtil.getOrCreateResized(originalDir, fileName, cacheDir, 800, 800, "jpg");
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(new FileSystemResource(cached));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
