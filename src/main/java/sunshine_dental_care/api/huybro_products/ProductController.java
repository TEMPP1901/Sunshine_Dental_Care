package sunshine_dental_care.api.huybro_products;

import jakarta.validation.Valid;
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
import org.springframework.web.multipart.MultipartFile;
import sunshine_dental_care.config.huybro_config.StaticResourceConfig;
import sunshine_dental_care.dto.huybro_products.*;
import sunshine_dental_care.services.huybro_products.gemini.dto.GeminiVisionResult;
import sunshine_dental_care.services.huybro_products.gemini.dto.ProductImageAnalyzeRequestDto;
import sunshine_dental_care.services.huybro_products.gemini.dto.ProductImageValidateRequestDto;
import sunshine_dental_care.services.huybro_products.gemini.services.product_image.moderation_validation.GeminiProductImageValidationService;
import sunshine_dental_care.services.huybro_products.gemini.services.product_image.moderation_vision.GeminiProductImageService;
import sunshine_dental_care.services.huybro_products.gemini.services.product_image.moderation_vision.GeminiProductImageServiceImpl;
import sunshine_dental_care.services.huybro_products.interfaces.ProductService;
import sunshine_dental_care.utils.huybro_utils.image.ImageCacheUtil;
import sunshine_dental_care.utils.huybro_utils.image.ImageUploadUtil;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);


    private final ProductService productService;
    private final StaticResourceConfig staticResourceConfig;
    private final GeminiProductImageService geminiProductImageService;
    private final GeminiProductImageValidationService validationService;

    public ProductController(ProductService productService, StaticResourceConfig staticResourceConfig, GeminiProductImageValidationService validationService, GeminiProductImageServiceImpl geminiProductImageService) {
        this.productService = productService;
        this.staticResourceConfig = staticResourceConfig;
        this.validationService = validationService;
        this.geminiProductImageService = geminiProductImageService;
    }

    // Hiển thị danh sách sản phẩm dành cho role public
    @GetMapping
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        List<ProductDto> products = productService.findAllProducts();
        if (products.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(products);
    }

    // Hiển thị chi tiết 1 sản phẩm theo id dành cho role public
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getProductById(@PathVariable Integer id) {
        ProductDto dto = productService.findById(id);
        if (dto == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(dto);
    }

    // Hiển thị hình ảnh đã resize
    // [CẬP NHẬT] resize 1000x1000 lấy hình ảnh
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

    // Phân trang cho danh sách sản phâm role public
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
        filter.setActive(true);
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

    //role: accountant

    // GET /api/products/accountant
    @GetMapping("/accountant")
    public ResponseEntity<List<ProductDto>> getAllProductsForAccountant() {
        List<ProductDto> products = productService.findAllProductsForAccountant();
        if (products.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(products);
    }

    // GET /api/products/accountant/page
    @GetMapping("/accountant/page")
    public PageResponseDto<ProductDto> pageForAccountant(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, name = "type") List<String> types
    ) {
        Sort sort = Sort.by(
                Sort.Order.desc("updatedAt"),
                Sort.Order.desc("createdAt")
        );
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

    // POST /api/products/accountant/create
    @PostMapping("/accountant/create")
    public ResponseEntity<ProductDto> createProductForAccountant(
            @Valid @RequestBody ProductCreateDto dto
    ) {
        ProductDto created = productService.createProduct(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // PUT /api/products/accountant/{id}
    @PutMapping("/accountant/update/{id}")
    public ResponseEntity<ProductDto> updateProductForAccountant(
            @PathVariable Integer id,
            @Valid @RequestBody ProductUpdateDto dto
    ) {
        ProductDto updated = productService.updateProduct(id, dto);
        return ResponseEntity.ok(updated);
    }

    // POST /api/products/accountant/suggest-sku
    @PostMapping("/accountant/suggest-sku")
    public ResponseEntity<String> suggestSkuForAccountant(
            @RequestBody List<String> typeNames
    ) {
        String sku = productService.suggestSku(typeNames);
        return ResponseEntity.ok(sku);
    }

    // POST /api/products/upload-image
    @PostMapping(
            value = "/upload-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> uploadProductImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sku") String sku,
            @RequestParam("imageOrder") Integer imageOrder
    ) {
        try {
            String imagePath = ImageUploadUtil.saveProductImage(
                    file,
                    sku,
                    staticResourceConfig.getOriginalDir()
            );

            ProductImageCreateDto dto = new ProductImageCreateDto();
            dto.setImageUrl(imagePath);
            dto.setImageOrder(imageOrder);

            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (IllegalArgumentException e) {
            log.warn("Upload image failed: {}", e.getMessage());

            Map<String, Object> body = new HashMap<>();
            body.put("globalErrors", List.of(e.getMessage()));

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(body);
        } catch (IOException e) {
            log.error("Upload image error", e);

            Map<String, Object> body = new HashMap<>();
            body.put("globalErrors", List.of("Cannot save image file"));

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(body);
        }
    }

    @PostMapping("/ai/validate-images")
    public ResponseEntity<?> validateImages(
            @RequestBody ProductImageValidateRequestDto request
    ) {
        try {
            validationService.validateProductImages(request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("[ProductAiValidationController] Validate images failed: {}", e.getMessage());

            Map<String, Object> body = new HashMap<>();
            body.put("globalErrors", List.of(e.getMessage()));

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(body);
        } catch (Exception e) {
            log.error("[ProductAiValidationController] Validate images error", e);

            Map<String, Object> body = new HashMap<>();
            body.put("globalErrors", List.of("AI validate image error"));

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(body);
        }
    }

    @PostMapping("/ai/analyze-images")
    public ResponseEntity<?> analyzeImages(
            @RequestBody ProductImageAnalyzeRequestDto request
    ) {
        try {
            GeminiVisionResult result = geminiProductImageService.analyzeProductImages(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("[ProductAiController] Analyze images failed: {}", e.getMessage());

            Map<String, Object> body = new HashMap<>();
            body.put("globalErrors", List.of(e.getMessage()));

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(body);
        } catch (Exception e) {
            log.error("[ProductAiController] Analyze images error", e);

            Map<String, Object> body = new HashMap<>();
            body.put("globalErrors", List.of("AI analyze image error"));

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(body);
        }
    }

}

