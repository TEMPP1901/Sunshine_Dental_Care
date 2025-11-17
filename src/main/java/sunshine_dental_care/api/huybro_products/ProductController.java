package sunshine_dental_care.api.huybro_products;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.config.huybro_config.StaticResourceConfig;
import sunshine_dental_care.config.huybro_config.enable.EnableTestOffSecurity;
import sunshine_dental_care.dto.huybro_products.PageResponseDto;
import sunshine_dental_care.dto.huybro_products.ProductDto;
import sunshine_dental_care.dto.huybro_products.ProductFilterDto;
import sunshine_dental_care.services.huybro_products.interfaces.ProductService;
import sunshine_dental_care.utils.huybro_utils.image.ImageCacheUtil;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;

@EnableTestOffSecurity
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);


    private final ProductService productService;
    private final StaticResourceConfig staticResourceConfig;

    public ProductController(ProductService productService, StaticResourceConfig staticResourceConfig) {
        this.productService = productService;
        this.staticResourceConfig = staticResourceConfig;
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
    // [CẬP NHẬT] resize 1000x1000
    @GetMapping("/images/{fileName}")
    public ResponseEntity<?> getResized(@PathVariable String fileName) {
        try {
            log.info("Tệp yêu cầu: {}", fileName);

            File cached = ImageCacheUtil.getOrCreateResized(staticResourceConfig.getOriginalDir(), fileName, staticResourceConfig.getCacheDir(), 1000, 1000, "jpg");

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(new FileSystemResource(cached));
        } catch (Exception e) {
            log.warn("Không thể trả ảnh đã resize cho '{}'. Lý do: {}", fileName, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    // GET /api/products/page
    @GetMapping("/page")
    public PageResponseDto<ProductDto> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            // sortBy: name | price | brand  (mặc định name)
            @RequestParam(defaultValue = "name") String sortBy,
            // order: asc | desc (mặc định asc)
            @RequestParam(defaultValue = "asc") String order,
            // --- filters ---
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, name = "type") List<String> types
    ) {
        // map sort key
        String sortField = switch (sortBy.toLowerCase()) {
            case "price" -> "defaultRetailPrice";
            case "brand" -> "brand";
            default -> "productName";
        };
        Sort sort = "desc".equalsIgnoreCase(order) ? Sort.by(sortField).descending()
                : Sort.by(sortField).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        ProductFilterDto filter = new ProductFilterDto();
        filter.setKeyword(keyword);
        filter.setBrands(brand);
        filter.setMinPrice(minPrice);
        filter.setMaxPrice(maxPrice);
        filter.setActive(active);
        filter.setTypes(types);

        Page<ProductDto> result = productService.search(filter, pageable);
        return new PageResponseDto<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }
}

