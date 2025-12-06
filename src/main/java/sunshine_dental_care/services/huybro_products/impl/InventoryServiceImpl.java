package sunshine_dental_care.services.huybro_products.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import sunshine_dental_care.dto.huybro_inventories.*;
import sunshine_dental_care.dto.huybro_products.*;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.huybro_products.Product;
import sunshine_dental_care.entities.huybro_products.ProductImage;
import sunshine_dental_care.entities.huybro_product_inventories.ProductInventory;
import sunshine_dental_care.entities.huybro_product_inventories.ProductStockReceipt;
import sunshine_dental_care.repositories.huybro_custom.ClinicRepository;
import sunshine_dental_care.repositories.huybro_products.ProductImageRepository;
import sunshine_dental_care.repositories.huybro_products.ProductInventoryRepository;
import sunshine_dental_care.repositories.huybro_products.ProductRepository;
import sunshine_dental_care.repositories.huybro_products.ProductStockReceiptRepository;
import sunshine_dental_care.services.huybro_products.interfaces.InventoryService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final ProductRepository productRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final ProductStockReceiptRepository stockReceiptRepository;
    private final ClinicRepository clinicRepository;
    private final ProductImageRepository productImageRepository;

    @Override
    public ProductStockReceiptDto importStock(ProductStockReceiptCreateDto dto) {
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
        Clinic clinic = clinicRepository.findById(dto.getClinicId())
                .orElseThrow(() -> new RuntimeException("Clinic not found"));

        // Check xem đã có giá chưa (defaultRetailPrice == null nghĩa là chưa có)
        boolean isFirstTimeSetup = (product.getDefaultRetailPrice() == null);

        BigDecimal importPrice;
        BigDecimal profitMargin;
        BigDecimal calculatedRetailPrice;
        String currency;

        if (isFirstTimeSetup) {
            // --- [CASE 1: LẦN ĐẦU TIÊN (SETUP)] ---

            // 1. Validate: Bắt buộc phải nhập giá nhập > 0
            if (dto.getImportPrice() == null || dto.getImportPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("This is the first import. You must specify Import Price and Margin to set the Global Retail Price.");
            }

            importPrice = dto.getImportPrice();
            profitMargin = dto.getProfitMargin() != null ? dto.getProfitMargin() : BigDecimal.ZERO;

            // Validate: Currency
            if (dto.getCurrency() == null) {
                currency = "USD"; // Hoặc ném lỗi tùy bạn
            } else {
                currency = dto.getCurrency();
            }

            // 2. Tính giá bán (Retail Price) = Import * (1 + Margin%)
            if (importPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal profitAmount = importPrice.multiply(profitMargin)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                calculatedRetailPrice = importPrice.add(profitAmount);
            } else {
                calculatedRetailPrice = BigDecimal.ZERO;
            }

        } else {
            // --- [CASE 2: ĐÃ CÓ GIÁ (NHẬP THÊM / NHẬP KHO KHÁC)] ---

            // 1. Luôn lấy Giá Nhập mới từ FE gửi lên (Lưu lịch sử biến động giá vốn)
            importPrice = dto.getImportPrice() != null ? dto.getImportPrice() : BigDecimal.ZERO;

            // 2. Giá bán giữ nguyên theo giá niêm yết hiện tại (Không đổi giá bán ở đây)
            calculatedRetailPrice = product.getDefaultRetailPrice();

            // 3. [LOGIC QUAN TRỌNG] Tự động tính lại Margin thực tế cho lô hàng này
            // Công thức: Margin = (RetailPrice / ImportPrice - 1) * 100
            // Giúp lưu lịch sử: "Lô này nhập 100 bán 120 thì lãi 20%, lô sau nhập 110 bán 120 thì lãi ít hơn"
            if (importPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = calculatedRetailPrice.divide(importPrice, 4, RoundingMode.HALF_UP);
                BigDecimal ratioMinusOne = ratio.subtract(BigDecimal.ONE);
                profitMargin = ratioMinusOne.multiply(BigDecimal.valueOf(100));
            } else {
                profitMargin = BigDecimal.ZERO;
            }

            // 4. Xử lý Currency (Ưu tiên lấy của Product cũ)
            if (product.getCurrency() != null) {
                currency = product.getCurrency();
            } else {
                currency = dto.getCurrency() != null ? dto.getCurrency() : "USD";
            }
        }

        // --- SAVE RECEIPT (Lưu lịch sử) ---
        ProductStockReceipt receipt = new ProductStockReceipt();
        receipt.setProduct(product);
        receipt.setClinic(clinic);
        receipt.setQuantityAdded(dto.getQuantityAdded());
        receipt.setImportPrice(importPrice);
        receipt.setProfitMargin(profitMargin); // Lưu Margin (tự tính hoặc nhập tay)
        receipt.setNewRetailPrice(calculatedRetailPrice);
        receipt.setCurrency(currency);
        receipt.setNote(dto.getNote());
        receipt.setCreatedAt(LocalDateTime.now());

        ProductStockReceipt savedReceipt = stockReceiptRepository.save(receipt);

        // --- UPDATE INVENTORY (Cộng dồn kho) ---
        updateInventory(product, clinic, dto.getQuantityAdded());

        // --- SYNC PRODUCT (Cập nhật trạng thái tổng) ---
        if (isFirstTimeSetup) {
            // Lần đầu: Update Giá + Currency + Unit + Active
            syncProductTotalState(product, calculatedRetailPrice, currency);
        } else {
            // Lần sau: Chỉ Update Unit + Active (Giữ nguyên giá cũ)
            // Truyền null vào giá để hàm sync biết không cần update giá
            syncProductTotalState(product, null, currency);
        }

        return mapToDto(savedReceipt);
    }

    // --- PRIVATE HELPERS ---

    private void updateInventory(Product product, Clinic clinic, Integer quantityAdded) {
        ProductInventory inventory = productInventoryRepository.findByProductIdAndClinicId(product.getId(), clinic.getId())
                .orElseGet(() -> {
                    ProductInventory newInv = new ProductInventory();
                    newInv.setProduct(product);
                    newInv.setClinic(clinic);
                    newInv.setQuantity(0);
                    newInv.setMinStockLevel(5);
                    return newInv;
                });

        inventory.setQuantity(inventory.getQuantity() + quantityAdded);
        inventory.setLastUpdated(LocalDateTime.now());
        productInventoryRepository.save(inventory);
    }

    private void syncProductInfo(Product product, BigDecimal newPrice, String currency) {
        Integer totalUnit = productInventoryRepository.sumTotalQuantityByProductId(product.getId());
        product.setUnit(totalUnit);

        // Luôn cập nhật giá mới nhất từ đợt nhập này
        if (newPrice != null && newPrice.compareTo(BigDecimal.ZERO) >= 0) {
            product.setDefaultRetailPrice(newPrice);
        }
        if (currency != null) {
            product.setCurrency(currency);
        }

        // Auto Active
        if (totalUnit > 0 && Boolean.FALSE.equals(product.getIsActive())) {
            product.setIsActive(true);
        }

        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
    }
    @Override
    public ProductInventoryStatusDto getProductInventoryStatus(Integer productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        List<ProductInventory> inventories = productInventoryRepository.findByProductId(productId);

        Integer total = 0;
        List<ProductInventoryStatusDto.ClinicInventoryDto> breakdown = new java.util.ArrayList<>();

        for (ProductInventory inv : inventories) {
            total += inv.getQuantity();
            breakdown.add(new ProductInventoryStatusDto.ClinicInventoryDto(
                    inv.getClinic().getId(),
                    inv.getClinic().getClinicName(),
                    inv.getQuantity()
            ));
        }

        if (product.getUnit() != null && product.getUnit() > total) {
            // Logic dự phòng nếu sync bị lệch, nhưng ở đây ta tin tưởng bảng Inventory hơn
        }

        return new ProductInventoryStatusDto(
                product.getId(),
                product.getProductName(),
                product.getSku(),
                total,
                breakdown
        );
    }
    // --- 3. GET PAGE (VIEW/EXPORT) ---
    @Override
    public PageResponseDto<InventoryViewDto> getInventoryPage(int page, int size, String keyword, Integer clinicId, String sortStr) {
        Sort sort = Sort.by(Sort.Direction.DESC, "lastUpdated");

        if ("quantity".equalsIgnoreCase(sortStr)) {
            sort = Sort.by(Sort.Direction.DESC, "quantity");
        }
        else if ("price".equalsIgnoreCase(sortStr)) {
            // sort by price logic...
        }

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<InventoryViewDto> pageResult = productInventoryRepository.searchInventory(keyword, clinicId, pageable);

        return new PageResponseDto<>(
                pageResult.getContent(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.isFirst(),
                pageResult.isLast()
        );
    }

    // --- 4. UPDATE INVENTORY (STRICT LOGIC) ---
    @Override
    @Transactional
    public InventoryViewDto updateInventoryQuantity(InventoryUpdateDto dto) {
        ProductInventory inventory = productInventoryRepository.findById(dto.getInventoryId())
                .orElseThrow(() -> new RuntimeException("Inventory record not found"));
        Product product = inventory.getProduct();

        boolean isChanged = false;
        Integer oldQuantity = inventory.getQuantity();

        // A. Update Quantity
        if (dto.getNewQuantity() != null && !inventory.getQuantity().equals(dto.getNewQuantity())) {
            inventory.setQuantity(dto.getNewQuantity());
            inventory.setLastUpdated(LocalDateTime.now());
            productInventoryRepository.save(inventory);
            isChanged = true;
        }

        // B. Update Retail Price
        if (dto.getNewRetailPrice() != null) {
            BigDecimal currentPrice = product.getDefaultRetailPrice() != null ? product.getDefaultRetailPrice() : BigDecimal.ZERO;
            if (currentPrice.compareTo(dto.getNewRetailPrice()) != 0) {
                product.setDefaultRetailPrice(dto.getNewRetailPrice());
                isChanged = true;
            }
        }

        // C. Update Currency
        if (dto.getNewCurrency() != null) {
            String currentCurrency = product.getCurrency();
            if (currentCurrency == null || !currentCurrency.equals(dto.getNewCurrency())) {
                product.setCurrency(dto.getNewCurrency());
                isChanged = true;
            }
        }

        // D. [FIXED] LƯU LỊCH SỬ & ĐỒNG BỘ
        if (isChanged) {
            // Luôn tạo Log Receipt khi có thay đổi (Bất kể có Note hay không)
            ProductStockReceipt logEntry = new ProductStockReceipt();
            logEntry.setProduct(product);
            logEntry.setClinic(inventory.getClinic());

            // Tính chênh lệch số lượng (nếu không đổi qty thì = 0)
            Integer quantityDifference = inventory.getQuantity() - oldQuantity;
            logEntry.setQuantityAdded(quantityDifference);

            // [QUAN TRỌNG] Lưu Giá Nhập và Margin từ DTO vào Lịch sử
            // Nếu DTO không gửi lên (null) thì để 0 (hoặc lấy giá cũ nếu muốn logic phức tạp hơn)
            logEntry.setImportPrice(dto.getNewImportPrice() != null ? dto.getNewImportPrice() : BigDecimal.ZERO);
            logEntry.setProfitMargin(dto.getNewProfitMargin() != null ? dto.getNewProfitMargin() : BigDecimal.ZERO);

            logEntry.setNewRetailPrice(product.getDefaultRetailPrice());
            logEntry.setCurrency(product.getCurrency());

            // Nếu không có Note thì ghi tự động
            String finalNote = (dto.getNote() != null && !dto.getNote().trim().isEmpty())
                    ? dto.getNote().trim()
                    : "Manual Update (Price/Qty)";
            logEntry.setNote(finalNote);

            logEntry.setCreatedAt(LocalDateTime.now());
            stockReceiptRepository.save(logEntry);

            // Đồng bộ Product tổng
            syncProductTotalState(product, null, null);
        }

        return mapToViewDto(inventory);
    }

    // --- PRIVATE HELPERS ---

    // Helper: Logic Siết Chặt Trạng Thái (Dùng chung cho cả Import và Update)
    private void syncProductTotalState(Product product, BigDecimal newPrice, String currency) {
        // 1. Tính tổng lại Unit từ tất cả các kho (Dựa vào dữ liệu thật trong DB)
        Integer totalUnit = productInventoryRepository.sumTotalQuantityByProductId(product.getId());
        if (totalUnit == null) totalUnit = 0;

        product.setUnit(totalUnit);

        // 2. Cập nhật Giá/Tiền tệ (Nếu có yêu cầu set cứng - thường dùng cho Import Stock)
        if (newPrice != null && newPrice.compareTo(BigDecimal.ZERO) >= 0) {
            product.setDefaultRetailPrice(newPrice);
        }
        if (currency != null) {
            product.setCurrency(currency);
        }

        // 3. Logic Auto Active/Inactive
        if (totalUnit == 0) {
            // Hết hàng toàn hệ thống -> Tự động Inactive để chặn bán
            product.setIsActive(false);
        } else {
            // Có hàng -> Tự động Active (nếu đang bị tắt do hết hàng trước đó)
            if (Boolean.FALSE.equals(product.getIsActive())) {
                product.setIsActive(true);
            }
        }

        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
    }

    @Override
    public InventoryViewDto getInventoryDetail(Integer inventoryId) {
        ProductInventory inventory = productInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new RuntimeException("Inventory record not found"));

        // Tái sử dụng hàm mapToViewDto đã viết ở bài trước
        return mapToViewDto(inventory);
    }

    private String resolveMainImageUrl(Integer productId) {
        List<ProductImage> images = productImageRepository.findByProduct_IdOrderByImageOrderAsc(productId);

        if (images == null || images.isEmpty()) return null;

        String rawPath = images.get(0).getImageUrl();
        if (rawPath == null || rawPath.isBlank()) return null;

        String fileName = Paths.get(rawPath).getFileName().toString();

        return "/api/products/images/" + fileName;
    }
    @Override
    public PageResponseDto<ProductStockHistoryDto> getProductStockHistory(Integer productId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Tìm tất cả hóa đơn của sản phẩm này
        Page<ProductStockReceipt> receiptPage = stockReceiptRepository.findByProductId(productId, pageable);

        List<ProductStockHistoryDto> content = receiptPage.getContent().stream().map(entity -> {
            ProductStockHistoryDto dto = new ProductStockHistoryDto();
            dto.setReceiptId(entity.getId());
            dto.setClinicName(entity.getClinic().getClinicName());
            dto.setQuantityAdded(entity.getQuantityAdded());
            dto.setImportPrice(entity.getImportPrice());
            dto.setProfitMargin(entity.getProfitMargin());
            dto.setNewRetailPrice(entity.getNewRetailPrice());
            dto.setNote(entity.getNote());
            dto.setCreatedAt(entity.getCreatedAt());
            dto.setCreatedBy("Admin"); // Hoặc lấy từ entity.getCreatedBy() nếu có
            return dto;
        }).toList();

        return new PageResponseDto<>(
                content,
                receiptPage.getNumber(),
                receiptPage.getSize(),
                receiptPage.getTotalElements(),
                receiptPage.getTotalPages(),
                receiptPage.isFirst(),
                receiptPage.isLast()
        );
    }

    // Mapper thủ công từ Entity -> ViewDTO (Vì query repository trả về DTO, nhưng update trả về Entity)
    private InventoryViewDto mapToViewDto(ProductInventory pi) {
        String imgUrl = resolveMainImageUrl(pi.getProduct().getId());
        return new InventoryViewDto(
                pi.getId(),
                pi.getProduct().getId(),
                pi.getProduct().getSku(),
                pi.getProduct().getProductName(),
                imgUrl,
                pi.getClinic().getId(),
                pi.getClinic().getClinicName(),
                pi.getQuantity(),
                pi.getProduct().getDefaultRetailPrice(),
                pi.getProduct().getCurrency(),
                pi.getMinStockLevel(),
                pi.getLastUpdated()
        );
    }
    private ProductStockReceiptDto mapToDto(ProductStockReceipt entity) {
        ProductStockReceiptDto dto = new ProductStockReceiptDto();
        dto.setReceiptId(entity.getId());
        dto.setProductId(entity.getProduct().getId());
        dto.setProductName(entity.getProduct().getProductName());
        dto.setClinicId(entity.getClinic().getId());
        dto.setClinicName(entity.getClinic().getClinicName());
        dto.setQuantityAdded(entity.getQuantityAdded());
        dto.setImportPrice(entity.getImportPrice());
        dto.setNewRetailPrice(entity.getNewRetailPrice());
        dto.setNote(entity.getNote());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setProfitMargin(entity.getProfitMargin());
        dto.setCurrency(entity.getCurrency());
        return dto;
    }
}