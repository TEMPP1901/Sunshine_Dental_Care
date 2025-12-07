package sunshine_dental_care.services.huybro_products.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.dto.huybro_invoices.ProductInvoiceDetailDto;
import sunshine_dental_care.dto.huybro_invoices.ProductInvoiceItemDto;
import sunshine_dental_care.dto.huybro_invoices.ProductInvoiceListDto;
import sunshine_dental_care.dto.huybro_invoices.UpdateInvoiceStatusRequestDto;
import sunshine_dental_care.entities.huybro_product_invoices.ProductInvoice;
import sunshine_dental_care.entities.huybro_product_invoices.ProductInvoiceItem;
import sunshine_dental_care.repositories.huybro_products.ProductInventoryRepository;
import sunshine_dental_care.repositories.huybro_products.ProductInvoiceItemRepository;
import sunshine_dental_care.repositories.huybro_products.ProductInvoiceRepository;
import sunshine_dental_care.services.huybro_checkout.email.client.EmailService;
import sunshine_dental_care.services.huybro_products.interfaces.ProductInvoiceService;
import sunshine_dental_care.utils.huybro_utils.EmailTemplateUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor // Tự động Inject các Repository final
public class ProductInvoiceServiceImpl implements ProductInvoiceService {

    private final ProductInvoiceRepository invoiceRepository;
    private final ProductInvoiceItemRepository invoiceItemRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final EmailService emailService;;

    @Override
    @Transactional(readOnly = true)
    public ProductInvoiceDetailDto getInvoiceDetail(Integer invoiceId) {
        ProductInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found with ID: " + invoiceId));

        // Dùng Query tối ưu (JOIN FETCH) đã viết ở bước trước
        List<ProductInvoiceItem> items = invoiceItemRepository.findAllByInvoiceIdWithProduct(invoiceId);

        return mapToDetailDto(invoice, items);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductInvoiceListDto> getInvoices(String status, String keyword, Pageable pageable) {
        return invoiceRepository.findAllSortedByLatestActivity(status, keyword, pageable)
                .map(this::mapToListDto);
    }

    @Override
    @Transactional
    public void updateInvoiceStatus(Integer invoiceId, UpdateInvoiceStatusRequestDto request) {
        ProductInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        String currentStatus = invoice.getInvoiceStatus();
        String newStatus = request.getNewStatus();

        log.info("Processing Invoice #{}: {} -> {}", invoice.getInvoiceCode(), currentStatus, newStatus);

        // --- STATE MACHINE VALIDATION (BE là nguồn sự thật) ---
        switch (newStatus) {
            case "CONFIRMED":
                // Chỉ được Confirm khi đang là NEW
                if (!"NEW".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException("Cannot CONFIRM an invoice that is " + currentStatus);
                }
                // [Logic] Check tồn kho lần cuối trước khi nhận đơn
                validateStockAvailability(invoiceId);
                break;

            case "PROCESSING":
                // Chỉ được Process khi đã Confirm
                if (!"CONFIRMED".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException("Cannot PROCESS an invoice that is " + currentStatus);
                }
                break;

            case "COMPLETED":
                // Chỉ hoàn thành khi đang xử lý
                if (!"PROCESSING".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException("Cannot COMPLETE an invoice that is " + currentStatus);
                }
                // [Logic] Đánh dấu thanh toán & Trừ kho thực tế (nếu logic yêu cầu trừ lúc này)
                invoice.setPaymentCompletedAt(Instant.now());
                invoice.setPaymentStatus("PAID");
                // TODO: Gọi hàm trừ kho thực tế ở đây (deductStock)
                break;

            case "CANCELLED":
                // Không thể hủy đơn đã hoàn thành
                if ("COMPLETED".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException("Cannot CANCEL a COMPLETED invoice");
                }
                // [Logic] Nếu đã trừ kho (ở bước trước đó), phải hoàn kho lại (Restock logic)
                break;

            default:
                throw new IllegalArgumentException("Invalid status: " + newStatus);
        }

        // Cập nhật Database
        invoice.setInvoiceStatus(newStatus);
        invoice.setNotes(request.getNote() != null ? request.getNote() : invoice.getNotes());
        invoice.setUpdatedAt(LocalDateTime.now());
        ProductInvoice savedInvoice = invoiceRepository.save(invoice);

        // [NEW] GỬI MAIL FULL HÓA ĐƠN + TRẠNG THÁI MỚI
        if (!currentStatus.equalsIgnoreCase(newStatus) && savedInvoice.getCustomerEmail() != null) {
            try {
                // 1. Phải lấy lại danh sách sản phẩm để in ra bảng (Full Bill)
                List<ProductInvoiceItem> items = invoiceItemRepository.findAllByInvoiceIdWithProduct(invoiceId);

                // 2. Tạo nội dung email
                String subject = "Cập nhật đơn hàng #" + savedInvoice.getInvoiceCode() + ": " + newStatus;
                String htmlBody = EmailTemplateUtils.buildInvoiceEmail(savedInvoice, items);

                // 3. Gửi
                emailService.sendHtmlEmail(savedInvoice.getCustomerEmail(), subject, htmlBody);
            } catch (Exception e) {
                log.warn("Failed to send status update email: {}", e.getMessage());
            }
        }

        invoiceRepository.save(invoice);
    }

    // --- PRIVATE HELPER METHODS ---

    private void validateStockAvailability(Integer invoiceId) {
        List<ProductInvoiceItem> items = invoiceItemRepository.findAllByInvoiceIdWithProduct(invoiceId);
        for (ProductInvoiceItem item : items) {
            Integer productId = item.getProduct().getId();
            Integer requiredQty = item.getQuantity();

            // Sử dụng hàm có sẵn của Inventory Repo
            Integer currentStock = productInventoryRepository.sumTotalQuantityByProductId(productId);
            if (currentStock == null) currentStock = 0;

            if (currentStock < requiredQty) {
                throw new IllegalStateException("Product " + item.getProductNameSnapshot() +
                        " is out of stock. Available: " + currentStock + ", Required: " + requiredQty);
            }
        }
    }

    private ProductInvoiceDetailDto mapToDetailDto(ProductInvoice inv, List<ProductInvoiceItem> items) {
        List<ProductInvoiceItemDto> itemDtos = items.stream().map(item -> ProductInvoiceItemDto.builder()
                .invoiceItemId(item.getId())
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productNameSnapshot(item.getProductNameSnapshot())
                .skuSnapshot(item.getSkuSnapshot())
                .quantity(item.getQuantity())
                .unitPriceBeforeTax(item.getUnitPriceBeforeTax())
                .taxRatePercent(item.getTaxRatePercent())
                .taxAmount(item.getTaxAmount())
                .lineTotalAmount(item.getLineTotalAmount())
                .note(item.getNote())
                .build()).collect(Collectors.toList());

        return ProductInvoiceDetailDto.builder()
                .invoiceId(inv.getId())
                .invoiceCode(inv.getInvoiceCode())
                .invoiceDate(inv.getInvoiceDate())
                .invoiceStatus(inv.getInvoiceStatus())
                .customerFullName(inv.getCustomerFullName())
                .customerPhone(inv.getCustomerPhone())
                .customerEmail(inv.getCustomerEmail())
                .shippingAddress(inv.getShippingAddress())
                .paymentMethod(inv.getPaymentMethod())
                .paymentStatus(inv.getPaymentStatus())
                .totalAmount(inv.getTotalAmount())
                .currency(inv.getCurrency())
                .createdAt(inv.getCreatedAt())
                .items(itemDtos)
                .build();
    }

    private ProductInvoiceListDto mapToListDto(ProductInvoice inv) {
        return ProductInvoiceListDto.builder()
                .invoiceId(inv.getId())
                .invoiceCode(inv.getInvoiceCode())
                .invoiceDate(inv.getInvoiceDate())
                .currency(inv.getCurrency())
                .customerFullName(inv.getCustomerFullName())
                .customerPhone(inv.getCustomerPhone())
                .totalAmount(inv.getTotalAmount())
                .invoiceStatus(inv.getInvoiceStatus())
                .paymentStatus(inv.getPaymentStatus())
                .paymentMethod(inv.getPaymentMethod())
                .createdAt(inv.getCreatedAt())
                .updatedAt(inv.getUpdatedAt())
                .build();
    }
}