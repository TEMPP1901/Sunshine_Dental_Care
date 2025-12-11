package sunshine_dental_care.services.impl.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.adminDTO.ChartDataDto;
import sunshine_dental_care.dto.adminDTO.RevenueReportDto;
import sunshine_dental_care.dto.adminDTO.TopProductDto;
import sunshine_dental_care.entities.huybro_product_invoice.ProductInvoice;
import sunshine_dental_care.entities.huybro_product_invoice.ProductInvoiceItem;
import sunshine_dental_care.repositories.huybro_products.ProductInvoiceItemRepository;
import sunshine_dental_care.repositories.huybro_products.ProductInvoiceRepository;
import sunshine_dental_care.services.interfaces.admin.AdminReportService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminReportServiceImpl implements AdminReportService {

    private final ProductInvoiceRepository productInvoiceRepo;
    private final ProductInvoiceItemRepository productInvoiceItemRepo;

    @Override
    @Transactional(readOnly = true)
    public RevenueReportDto getRevenueReport(LocalDate startDate, LocalDate endDate, String currency) {
        try {
            // Xác định khoảng ngày báo cáo: 30 ngày gần nhất nếu không chỉ định
            final LocalDate finalEndDate = (endDate != null) ? endDate : LocalDate.now();
            final LocalDate finalStartDate = (startDate != null) ? startDate : finalEndDate.minusDays(30);

            log.debug("Getting revenue report from {} to {}, currency: {}", finalStartDate, finalEndDate, currency);

            // Lấy tất cả hóa đơn trong khoảng thời gian & theo loại tiền nếu có
            List<ProductInvoice> allInvoices = productInvoiceRepo.findAll().stream()
                .filter(inv -> {
                    if (inv.getInvoiceDate() == null) return false;
                    return !inv.getInvoiceDate().isBefore(finalStartDate) && !inv.getInvoiceDate().isAfter(finalEndDate);
                })
                .filter(inv -> {
                    if (currency != null && !currency.isEmpty()) {
                        String invCurrency = inv.getCurrency();
                        return invCurrency != null && invCurrency.equalsIgnoreCase(currency);
                    }
                    return true;
                })
                .collect(Collectors.toList());

            // Tổng doanh thu đã thanh toán
            BigDecimal netRevenue = allInvoices.stream()
                .filter(inv -> {
                    String paymentStatus = inv.getPaymentStatus();
                    return paymentStatus != null && (paymentStatus.equals("PAID") || paymentStatus.equals("COMPLETED"));
                })
                .map(ProductInvoice::getTotalAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Doanh thu tiềm năng (chưa thanh toán)
            BigDecimal potentialRevenue = allInvoices.stream()
                .filter(inv -> {
                    String paymentStatus = inv.getPaymentStatus();
                    return paymentStatus == null || (!paymentStatus.equals("PAID") && !paymentStatus.equals("COMPLETED"));
                })
                .map(ProductInvoice::getTotalAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Doanh thu bị mất (invoice đã hủy). Trả về 0 do không có cột trạng thái huỷ
            BigDecimal lostRevenue = BigDecimal.ZERO;

            // Số lượng đơn hàng đã hoàn thành (đã thanh toán)
            Long totalOrdersCompleted = allInvoices.stream()
                .filter(inv -> {
                    String paymentStatus = inv.getPaymentStatus();
                    return paymentStatus != null && (paymentStatus.equals("PAID") || paymentStatus.equals("COMPLETED"));
                })
                .count();

            // Đơn hàng bị huỷ - không xác định được, trả về 0
            Long totalOrdersCancelled = 0L;

            // Thống kê doanh thu theo từng ngày (dùng cho chart)
            Map<LocalDate, List<ProductInvoice>> invoicesByDate = allInvoices.stream()
                .filter(inv -> {
                    String paymentStatus = inv.getPaymentStatus();
                    return paymentStatus != null && (paymentStatus.equals("PAID") || paymentStatus.equals("COMPLETED"));
                })
                .collect(Collectors.groupingBy(ProductInvoice::getInvoiceDate));

            List<ChartDataDto> chartData = new ArrayList<>();
            LocalDate current = finalStartDate;
            while (!current.isAfter(finalEndDate)) {
                List<ProductInvoice> dayInvoices = invoicesByDate.getOrDefault(current, new ArrayList<>());
                BigDecimal revenue = dayInvoices.stream()
                        .map(ProductInvoice::getTotalAmount)
                        .filter(amount -> amount != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                Long orderCount = (long) dayInvoices.size();

                chartData.add(ChartDataDto.builder()
                        .date(current)
                        .revenue(revenue)
                        .orderCount(orderCount)
                        .build());
                current = current.plusDays(1);
            }

            // Lấy tất cả ProductInvoiceItem để thống kê sp bán chạy (tránh lazy loading)
            List<ProductInvoiceItem> allItems = productInvoiceItemRepo.findAll();
            log.debug("Found {} invoice items total", allItems.size());

            // Map để truy xuất hóa đơn nhanh theo id, tránh lazy loading
            Map<Integer, ProductInvoice> invoiceMap = allInvoices.stream()
                    .collect(Collectors.toMap(ProductInvoice::getId, inv -> inv));

            // Lọc item theo ngày và hóa đơn đã thanh toán
            List<ProductInvoiceItem> filteredItems = allItems.stream()
                .filter(item -> {
                    try {
                        ProductInvoice invoice = (item.getInvoice() != null && item.getInvoice().getId() != null) 
                                ? invoiceMap.get(item.getInvoice().getId()) 
                                : null;
                        if (invoice == null || invoice.getInvoiceDate() == null) return false;
                        return !invoice.getInvoiceDate().isBefore(finalStartDate) && !invoice.getInvoiceDate().isAfter(finalEndDate);
                    } catch (Exception e) {
                        log.warn("Lỗi truy xuất hóa đơn cho item {}: {}", item.getId(), e.getMessage());
                        return false;
                    }
                })
                .filter(item -> {
                    try {
                        ProductInvoice invoice = (item.getInvoice() != null && item.getInvoice().getId() != null) 
                                ? invoiceMap.get(item.getInvoice().getId()) 
                                : null;
                        if (invoice == null) return false;
                        String paymentStatus = invoice.getPaymentStatus();
                        return paymentStatus != null && (paymentStatus.equals("PAID") || paymentStatus.equals("COMPLETED"));
                    } catch (Exception e) {
                        log.warn("Lỗi kiểm tra trạng thái thanh toán cho item {}: {}", item.getId(), e.getMessage());
                        return false;
                    }
                })
                .collect(Collectors.toList());

            log.debug("Filtered to {} items after date and payment status filter", filteredItems.size());

            // Gom nhóm sản phẩm bán chạy theo tên+sku
            Map<String, List<ProductInvoiceItem>> itemsByProduct = filteredItems.stream()
                .collect(Collectors.groupingBy(item -> {
                    String name = item.getProductNameSnapshot();
                    String sku = item.getSkuSnapshot();
                    return (name != null ? name : "Unknown") + "|" + (sku != null ? sku : "");
                }));

            List<TopProductDto> topProducts = itemsByProduct.entrySet().stream()
                .map(entry -> {
                    List<ProductInvoiceItem> items = entry.getValue();
                    String[] parts = entry.getKey().split("\\|");
                    String productName = parts.length > 0 ? parts[0] : "Unknown";
                    String sku = parts.length > 1 ? parts[1] : "";

                    Long totalSoldQty = items.stream()
                            .map(ProductInvoiceItem::getQuantity)
                            .filter(qty -> qty != null)
                            .mapToLong(Integer::longValue)
                            .sum();

                    BigDecimal totalRevenue = items.stream()
                            .map(ProductInvoiceItem::getLineTotalAmount)
                            .filter(amount -> amount != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return TopProductDto.builder()
                            .productName(productName)
                            .sku(sku)
                            .totalSoldQty(totalSoldQty)
                            .totalRevenue(totalRevenue)
                            .build();
                })
                .sorted((a, b) -> b.getTotalRevenue().compareTo(a.getTotalRevenue()))
                .limit(10) // Lấy TOP 10 sản phẩm doanh thu cao nhất
                .collect(Collectors.toList());

            RevenueReportDto result = RevenueReportDto.builder()
                .netRevenue(netRevenue)
                .potentialRevenue(potentialRevenue)
                .lostRevenue(lostRevenue)
                .totalOrdersCompleted(totalOrdersCompleted)
                .totalOrdersCancelled(totalOrdersCancelled)
                .chartData(chartData)
                .topProducts(topProducts)
                .build();

            log.debug("Revenue report generated successfully. Net revenue: {}, Completed orders: {}", 
                    netRevenue, totalOrdersCompleted);

            return result;
        } catch (Exception e) {
            log.error("Error generating revenue report: ", e);
            // Nếu có lỗi thì trả về kết quả rỗng để FE vẫn hiển thị được
            return RevenueReportDto.builder()
                    .netRevenue(BigDecimal.ZERO)
                    .potentialRevenue(BigDecimal.ZERO)
                    .lostRevenue(BigDecimal.ZERO)
                    .totalOrdersCompleted(0L)
                    .totalOrdersCancelled(0L)
                    .chartData(new ArrayList<>())
                    .topProducts(new ArrayList<>())
                    .build();
        }
    }
}
