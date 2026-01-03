package sunshine_dental_care.services.huybro_reports.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.dto.huybro_reports.RevenueReportResponseDto;
import sunshine_dental_care.repositories.huybro_products.ProductInvoiceItemRepository;
import sunshine_dental_care.repositories.huybro_products.ProductInvoiceRepository;
import sunshine_dental_care.services.huybro_reports.interfaces.IRevenueReportService;
import sunshine_dental_care.utils.huybro_utils.ExcelExportUtils;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime; // [IMPORT QUAN TRỌNG]
import java.time.LocalTime;     // [IMPORT QUAN TRỌNG]
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueReportServiceImpl implements IRevenueReportService {

    private final ProductInvoiceRepository invoiceRepository;
    private final ProductInvoiceItemRepository invoiceItemRepository;

    @Override
    @Transactional(readOnly = true)
    public RevenueReportResponseDto getRevenueDashboardData(LocalDate startDate, LocalDate endDate, String targetCurrency) {
        // 1. Chuẩn hóa Input (Validation & Defaults)
        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null) endDate = LocalDate.now();

        // [QUAN TRỌNG] Chuyển đổi LocalDate (chỉ có ngày) sang LocalDateTime (ngày + giờ)
        // Lý do: DB lưu createdAt là DateTime. Nếu chỉ tìm theo Date, nó sẽ hiểu là 00:00:00 -> Mất dữ liệu trong ngày cuối.
        LocalDateTime startDateTime = startDate.atStartOfDay();             // Ví dụ: 2024-01-01 00:00:00
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);          // Ví dụ: 2024-01-31 23:59:59.999999

        // Chuẩn hóa currency: Chỉ chấp nhận USD hoặc VND, mặc định là VND
        String safeCurrency = normalizeCurrency(targetCurrency);

        log.info("Generating Revenue Report from {} to {} in {}", startDateTime, endDateTime, safeCurrency);

        // 2. Tính toán KPI (Repository đã xử lý logic 'CASE WHEN' và 'createdAt')

        // a. Doanh thu thực (Chỉ tính COMPLETED)
        BigDecimal netRevenue = invoiceRepository.sumTotalRevenueConverted(
                List.of("COMPLETED"), startDateTime, endDateTime, safeCurrency
        );

        // b. Doanh thu tiềm năng (Pipeline: NEW -> PROCESSING)
        BigDecimal potentialRevenue = invoiceRepository.sumTotalRevenueConverted(
                Arrays.asList("NEW", "CONFIRMED", "PROCESSING"), startDateTime, endDateTime, safeCurrency
        );

        // c. Doanh thu mất đi (Hủy/Hoàn trả)
        BigDecimal lostRevenue = invoiceRepository.sumTotalRevenueConverted(
                Arrays.asList("CANCELLED", "RETURNED"), startDateTime, endDateTime, safeCurrency
        );

        // d. Số lượng đơn (Count không cần quy đổi tiền)
        Long completedOrders = invoiceRepository.countByStatusAndDate(List.of("COMPLETED"), startDateTime, endDateTime);
        Long cancelledOrders = invoiceRepository.countByStatusAndDate(List.of("CANCELLED"), startDateTime, endDateTime);

        // 3. Lấy dữ liệu Biểu đồ (Repository trả về List<RevenueChartDataDto>)
        var chartData = invoiceRepository.getRevenueChartDataConverted(startDateTime, endDateTime, safeCurrency);

        // 4. Lấy Top Sản phẩm bán chạy (Top 5)
        var topProducts = invoiceItemRepository.findTopSellingProductsConverted(
                startDateTime, endDateTime, safeCurrency, PageRequest.of(0, 5)
        );

        // 5. Format số liệu (Rounding)
        // Nếu là VND -> 0 số thập phân. Nếu USD -> 2 số thập phân.
        int scale = "VND".equals(safeCurrency) ? 0 : 2;

        return RevenueReportResponseDto.builder()
                .netRevenue(formatMoney(netRevenue, scale))
                .potentialRevenue(formatMoney(potentialRevenue, scale))
                .lostRevenue(formatMoney(lostRevenue, scale))
                .totalOrdersCompleted(completedOrders)
                .totalOrdersCancelled(cancelledOrders)
                .chartData(chartData)
                .topProducts(topProducts)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ByteArrayInputStream exportToExcel(LocalDate startDate, LocalDate endDate, String targetCurrency) {
        // Tái sử dụng logic lấy data để đảm bảo tính nhất quán số liệu
        // Service này sẽ tự convert LocalDate -> LocalDateTime bên trong hàm getRevenueDashboardData
        RevenueReportResponseDto data = getRevenueDashboardData(startDate, endDate, targetCurrency);

        // Gọi Utils để render file Excel
        // Lưu ý: Utils vẫn nhận LocalDate để in tiêu đề báo cáo cho đẹp (không cần in giờ phút giây lên tiêu đề Excel)
        return ExcelExportUtils.exportRevenueReportToExcel(
                data,
                startDate != null ? startDate : LocalDate.now().minusDays(30),
                endDate != null ? endDate : LocalDate.now()
        );
    }

    // --- Private Helpers ---

    private String normalizeCurrency(String input) {
        if (input == null || input.trim().isEmpty()) return "VND";
        String upper = input.trim().toUpperCase();
        return ("USD".equals(upper)) ? "USD" : "VND";
    }

    private BigDecimal formatMoney(BigDecimal amount, int scale) {
        if (amount == null) return BigDecimal.ZERO;
        return amount.setScale(scale, RoundingMode.HALF_UP);
    }
}