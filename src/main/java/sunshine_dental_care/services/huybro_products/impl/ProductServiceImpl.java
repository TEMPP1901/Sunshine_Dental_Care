package sunshine_dental_care.services.huybro_products.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.dto.huybro_products.*;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.huybro_product_inventories.ProductInventory;
import sunshine_dental_care.entities.huybro_products.*;
import sunshine_dental_care.repositories.huybro_custom.ClinicRepository;
import sunshine_dental_care.repositories.huybro_products.*;
import sunshine_dental_care.services.huybro_products.interfaces.ProductService;
import sunshine_dental_care.utils.huybro_utils.format.FormatTypeProduct;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductsProductTypeRepository productsProductTypeRepository;
    private final ProductTypeRepository productTypeRepository;
    private final ClinicRepository clinicRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final ProductStockReceiptRepository stockReceiptRepository;
    private final ProductInvoiceItemRepository productInvoiceItemRepository;

    public ProductServiceImpl(ProductRepository productRepository,
                              ProductImageRepository productImageRepository,
                              ProductsProductTypeRepository productsProductTypeRepository,
                              ProductTypeRepository productTypeRepository,
                              ClinicRepository clinicRepository,
                              ProductInventoryRepository productInventoryRepository,
                              ProductStockReceiptRepository stockReceiptRepository,
                              ProductInvoiceItemRepository productInvoiceItemRepository) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.productsProductTypeRepository = productsProductTypeRepository;
        this.productTypeRepository = productTypeRepository;
        this.clinicRepository = clinicRepository;
        this.productInventoryRepository = productInventoryRepository;
        this.stockReceiptRepository = stockReceiptRepository;
        this.productInvoiceItemRepository = productInvoiceItemRepository;
    }

    // Hàm map từ Entity → DTO
    private ProductDto mapToDto(Product product) {
        ProductDto dto = new ProductDto();
        dto.setProductId(product.getId());
        dto.setSku(product.getSku());
        dto.setProductName(product.getProductName());
        dto.setBrand(product.getBrand());
        dto.setProductDescription(product.getProductDescription());
        dto.setUnit(product.getUnit());
        dto.setDefaultRetailPrice(product.getDefaultRetailPrice());
        dto.setCurrency(product.getCurrency());
        dto.setIsTaxable(product.getIsTaxable());
        dto.setTaxCode(product.getTaxCode());
        dto.setIsActive(product.getIsActive());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());

        // Lấy danh sách ảnh
        var images = productImageRepository.findByProduct_IdOrderByImageOrderAsc(product.getId())
                .stream().map(img -> {
                    var it = new ProductImageDto();
                    it.setImageId(img.getId());
                    it.setImageUrl(img.getImageUrl());
                    it.setImageOrder(img.getImageOrder());
                    return it;
                }).toList();
        dto.setImage(images);

        // Lấy danh sách loại
        List<String> typeNames = productsProductTypeRepository.findByProduct_Id(product.getId())
                .stream()
                .map(ppt -> ppt.getType().getTypeName())
                .collect(Collectors.toList());
        dto.setTypeNames(typeNames);

        stockReceiptRepository.findTopByProduct_IdOrderByCreatedAtDesc(product.getId())
                .ifPresentOrElse(
                        receipt -> {
                            // Trường hợp CÓ lịch sử nhập hàng
                            dto.setLatestImportPrice(receipt.getImportPrice());
                            dto.setLatestProfitMargin(receipt.getProfitMargin());
                        },
                        () -> {
                            // Trường hợp KHÔNG CÓ lịch sử (Sản phẩm mới) -> Set về 0
                            dto.setLatestImportPrice(BigDecimal.ZERO);
                            dto.setLatestProfitMargin(BigDecimal.ZERO);
                        }
                );

        return dto;
    }

    @Override
    public List<ProductDto> findAllProducts() {
        List<Product> products = productRepository.findByIsActiveTrue();
        return products.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    public List<ProductDto> findAllProductsForAccountant() {
        List<Product> products = productRepository.findAll();
        return products.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    public ProductDto findById(Integer id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        ProductDto dto = mapToDto(product);

        // 1. SỐ LIỆU: TỒN KHO & ĐÃ BÁN
        // Unit (Tồn kho) đã có trong Product Entity (được sync từ InventoryService)
        // Sold Count (Đã bán): Query từ InvoiceItem
        Integer sold = productInvoiceItemRepository.sumSoldQuantityByProductId(id);
        dto.setSoldCount(sold != null ? sold : 0);

        // 2. SOCIAL PROOF: LỊCH SỬ MUA (Lấy 10 người gần nhất)
        List<ProductPurchaseHistoryDto> recentPurchases = productInvoiceItemRepository
                .findRecentPurchases(id, PageRequest.of(0, 10));
        dto.setRecentPurchases(recentPurchases);

        // --- 3. LOGIC GIẢM GIÁ (SALE %) - [FIXED FINAL: NULL SAFETY] ---
        BigDecimal maxHistoryPrice = stockReceiptRepository.findMaxRetailPriceByProductId(id);
        BigDecimal currentPrice = product.getDefaultRetailPrice();

        dto.setOriginalPrice(null);
        dto.setDiscountPercentage(null);

        if (maxHistoryPrice != null && maxHistoryPrice.compareTo(BigDecimal.ZERO) > 0
                && currentPrice != null && currentPrice.compareTo(maxHistoryPrice) < 0) {

            // Tính % giảm giá
            BigDecimal diff = maxHistoryPrice.subtract(currentPrice);

            // Công thức: (Diff * 100) / Max
            // Scale 2, Làm tròn HALF_UP
            BigDecimal percent = diff.multiply(BigDecimal.valueOf(100))
                    .divide(maxHistoryPrice, 2, RoundingMode.HALF_UP);

            if (percent.compareTo(BigDecimal.ZERO) > 0) {
                dto.setOriginalPrice(maxHistoryPrice);
                dto.setDiscountPercentage(percent);
            }
        }

        // --- 4. SẢN PHẨM GỢI Ý (RELATED PRODUCTS) ---

        // B1: Lấy danh sách Type của sản phẩm hiện tại (đã có sẵn trong DTO sau khi map)
        List<String> currentTypeNames = dto.getTypeNames();

        List<Product> related;

        // B2: Nếu sản phẩm có Type -> Tìm theo (Brand + Type)
        if (currentTypeNames != null && !currentTypeNames.isEmpty()) {
            related = productRepository.findRelatedProducts(
                    id,
                    product.getBrand(),
                    currentTypeNames,
                    PageRequest.of(0, 4) // Lấy Top 4
            );

            // B3: (Optional) Nếu tìm theo Type + Brand mà ít quá (ví dụ < 2),
            // có thể fallback tìm chỉ theo Brand để lấp đầy UI (Tuỳ bạn chọn, ở đây tôi code kỹ logic này)
            if (related.size() < 4) {
                // Logic phụ: Nếu thích thì implement thêm việc fill cho đủ 4,
                // nhưng để đơn giản theo yêu cầu của bạn thì cứ trả về kết quả tìm được.
            }

        } else {
            // B4: Nếu sản phẩm hiện tại không có Type nào -> Chỉ tìm theo Brand
            related = productRepository.findTop4ByBrandAndIdNotAndIsActiveTrue(product.getBrand(), id);
        }

        dto.setRelatedProducts(related.stream().map(this::mapToDto).collect(Collectors.toList()));

        return dto;
    }
    @Override
    public Page<ProductDto> search(ProductFilterDto filter, Pageable pageable) {
        String keyword = (filter != null && filter.getKeyword() != null && !filter.getKeyword().isBlank())
                ? filter.getKeyword().trim()
                : null;

        boolean brandEnabled = filter != null && filter.getBrands() != null && !filter.getBrands().isEmpty();
        boolean typeEnabled  = filter != null && filter.getTypes()  != null && !filter.getTypes().isEmpty();

        var brands = brandEnabled ? filter.getBrands() : List.<String>of();
        var types  = typeEnabled  ? filter.getTypes()  : List.<String>of();

        var page = productRepository.searchProducts(
                keyword,
                brands, brandEnabled,
                types,  typeEnabled,
                filter != null ? filter.getMinPrice() : null,
                filter != null ? filter.getMaxPrice() : null,
                filter != null ? filter.getActive()   : null,
                pageable
        );
        return page.map(this::mapToDto);
    }

    // --- CREATE PRODUCT (STRICT LOGIC) ---
    @Override
    @Transactional
    public ProductDto createProduct(ProductCreateDto dto) {
        LocalDateTime now = LocalDateTime.now();

        Product product = new Product();
        product.setSku(dto.getSku().trim());
        product.setProductName(dto.getProductName().trim());
        product.setBrand(dto.getBrand().trim());
        product.setProductDescription(dto.getProductDescription().trim());

        // --- 1. FORCE SET (LOGIC QUAN TRỌNG) ---
        // Bắt buộc set về 0 và false, bất kể DTO gửi lên cái gì
        product.setUnit(0);
        product.setDefaultRetailPrice(null); // Chưa nhập hàng -> Chưa có giá
        product.setCurrency(null);           // Chưa nhập hàng -> Chưa có tiền tệ
        product.setIsActive(false);          // Kho = 0 -> Tắt Active

        // --- 2. Xử lý Tax ---
        Boolean taxableFlag = dto.getIsTaxable();
        BigDecimal tax = dto.getTaxCode();
        boolean taxable;
        BigDecimal finalTax;

        if (Boolean.FALSE.equals(taxableFlag)) {
            taxable = false;
            finalTax = BigDecimal.ZERO;
        } else {
            if (tax != null && tax.compareTo(BigDecimal.ZERO) > 0) {
                taxable = true;
                finalTax = tax;
            } else {
                taxable = false;
                finalTax = BigDecimal.ZERO;
            }
        }
        product.setIsTaxable(taxable);
        product.setTaxCode(finalTax);

        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        Product savedProduct = productRepository.save(product);

        // --- 3. Xử lý Image & Type (Giữ nguyên code của bạn) ---
        List<ProductImage> images = new ArrayList<>();
        if (dto.getImage() != null) {
            for (ProductImageCreateDto imgDto : dto.getImage()) {
                ProductImage img = new ProductImage();
                img.setProduct(savedProduct);
                img.setImageUrl(imgDto.getImageUrl().trim());
                img.setImageOrder(imgDto.getImageOrder());
                img.setCreatedAt(now);
                images.add(img);
            }
            productImageRepository.saveAll(images);
        }

        List<ProductType> types = productTypeRepository.findByTypeNameIn(dto.getTypeNames());
        if (types.size() != dto.getTypeNames().size()) {
            throw new IllegalArgumentException("Some product types were not found");
        }

        List<ProductsProductType> pptList = new ArrayList<>();
        for (ProductType type : types) {
            ProductsProductType ppt = new ProductsProductType();
            ppt.setId(new ProductsProductTypeId(savedProduct.getId(), type.getId()));
            ppt.setProduct(savedProduct);
            ppt.setType(type);
            pptList.add(ppt);
        }
        productsProductTypeRepository.saveAll(pptList);

        // --- 4. AUTO INIT INVENTORY (Tạo kho rỗng) ---
        // Tự động tạo kho rỗng cho tất cả các chi nhánh hiện có
        List<Clinic> clinics = clinicRepository.findAll();
        for (Clinic clinic : clinics) {
            ProductInventory inv = new ProductInventory();
            inv.setProduct(savedProduct);
            inv.setClinic(clinic);
            inv.setQuantity(0);      // Kho rỗng
            inv.setMinStockLevel(5); // Default warning level
            inv.setLastUpdated(now);
            productInventoryRepository.save(inv);
        }

        return mapToDto(savedProduct);
    }

    // --- UPDATE PRODUCT (STRICT LOGIC) ---
    @Override
    @Transactional
    public ProductDto updateProduct(Integer id, ProductUpdateDto dto) {
        LocalDateTime now = LocalDateTime.now();

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        product.setSku(dto.getSku().trim());
        product.setProductName(dto.getProductName().trim());
        product.setBrand(dto.getBrand().trim());
        product.setProductDescription(dto.getProductDescription().trim());

        // --- 1. STRICT IGNORE ---
        // TUYỆT ĐỐI KHÔNG CẬP NHẬT: unit, defaultRetailPrice, currency từ DTO
        // product.setUnit(...) -> XÓA
        // product.setDefaultRetailPrice(...) -> XÓA
        // product.setCurrency(...) -> XÓA

        // --- 2. Xử lý Active (Có điều kiện) ---
        if (dto.getIsActive() != null) {
            if (dto.getIsActive()) {
                // Nếu muốn BẬT -> Phải check xem có hàng không?
                // Logic: Lấy tổng unit hiện tại trong DB
                Integer currentUnit = product.getUnit();
                if (currentUnit == null || currentUnit <= 0) {
                    // Tùy bạn: Ném lỗi hoặc tự động set về false
                    // Ở đây tôi chọn cách an toàn: Nếu kho = 0 thì không cho bật
                    throw new IllegalArgumentException("Cannot activate product because Total Stock is 0. Please import stock first.");
                }
                product.setIsActive(true);
            } else {
                // Nếu muốn TẮT -> Cho phép tắt thoải mái
                product.setIsActive(false);
            }
        }

        // --- 3. Xử lý Tax ---
        Boolean taxableFlag = dto.getIsTaxable();
        BigDecimal tax = dto.getTaxCode();
        boolean taxable;
        BigDecimal finalTax;

        if (Boolean.FALSE.equals(taxableFlag)) {
            taxable = false;
            finalTax = BigDecimal.ZERO;
        } else {
            if (tax != null && tax.compareTo(BigDecimal.ZERO) > 0) {
                taxable = true;
                finalTax = tax;
            } else {
                taxable = false;
                finalTax = BigDecimal.ZERO;
            }
        }
        product.setIsTaxable(taxable);
        product.setTaxCode(finalTax);

        product.setUpdatedAt(now);
        productRepository.save(product);

        // --- 4. Xử lý Image & Type (Giữ nguyên logic của bạn) ---
        List<ProductImage> existingImages = productImageRepository.findByProduct_IdOrderByImageOrderAsc(id);
        if (existingImages.size() != 3) {
            throw new IllegalStateException("Product must have exactly 3 images in database");
        }
        Map<Integer, ProductImage> mapByOrder = existingImages.stream()
                .collect(Collectors.toMap(ProductImage::getImageOrder, img -> img));

        if (dto.getImage() != null) {
            for (ProductImageUpdateDto imgDto : dto.getImage()) {
                ProductImage img = mapByOrder.get(imgDto.getImageOrder());
                if (img != null) {
                    img.setImageUrl(imgDto.getImageUrl().trim());
                }
            }
        }
        productImageRepository.saveAll(existingImages);

        List<ProductsProductType> oldLinks = productsProductTypeRepository.findByProduct_Id(id);
        if (!oldLinks.isEmpty()) {
            productsProductTypeRepository.deleteAll(oldLinks);
        }

        List<ProductType> types = productTypeRepository.findByTypeNameIn(dto.getTypeNames());
        if (types.size() != dto.getTypeNames().size()) {
            throw new IllegalArgumentException("Some product types were not found");
        }

        List<ProductsProductType> pptList = new ArrayList<>();
        for (ProductType type : types) {
            ProductsProductType ppt = new ProductsProductType();
            ppt.setId(new ProductsProductTypeId(product.getId(), type.getId()));
            ppt.setProduct(product);
            ppt.setType(type);
            pptList.add(ppt);
        }
        productsProductTypeRepository.saveAll(pptList);

        return mapToDto(product);
    }

    @Override
    public String suggestSku(List<String> typeNames) {
        Integer maxId = productRepository.findMaxId();
        int nextId = (maxId == null ? 1 : maxId + 1);

        if (typeNames == null || typeNames.isEmpty()) {
            return String.valueOf(nextId);
        }

        List<ProductType> types = productTypeRepository.findByTypeNameIn(typeNames);

        List<String> codes = types.stream()
                .map(t -> FormatTypeProduct.autoTypeCode(t.getTypeName()))
                .sorted()
                .toList();

        if (codes.isEmpty()) {
            return String.valueOf(nextId);
        }

        return nextId + "_" + String.join("_", codes);
    }

}
