package sunshine_dental_care.repositories.huybro_products;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.dto.huybro_reports.RevenueChartDataDto;
import sunshine_dental_care.entities.huybro_product_invoices.ProductInvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductInvoiceRepository extends JpaRepository<ProductInvoice, Integer> {

    // 1. Kiểm tra tồn tại mã đơn (Cần thiết cho logic tạo mã unique)
    boolean existsByInvoiceCode(String invoiceCode);

    // 2. Tìm chi tiết đơn hàng (Có thể dùng findById có sẵn, nhưng nếu cần custom fetch thì giữ)
    // Optional<ProductInvoice> findByInvoiceCode(String invoiceCode); // Tạm comment nếu chưa dùng

    // 3. [MAIN QUERY] Hàm duy nhất dùng cho trang danh sách (Lọc + Sort thông minh)
    @Query("SELECT i FROM ProductInvoice i " +
            "WHERE (:status IS NULL OR i.invoiceStatus = :status) " +
            "AND (:keyword IS NULL OR :keyword = '' OR i.invoiceCode LIKE %:keyword% OR i.customerPhone LIKE %:keyword%) " +
            "ORDER BY COALESCE(i.updatedAt, i.createdAt) DESC")
    Page<ProductInvoice> findAllSortedByLatestActivity(
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // [FIXED] Sửa lỗi Type Mismatch bằng cách dùng 25000.0 (Double) và CASE WHEN tường minh
    @Query("""
        SELECT COALESCE(SUM(
            CASE 
                WHEN :targetCurrency = i.currency THEN i.totalAmount
                
                WHEN :targetCurrency = 'VND' AND i.currency = 'USD' 
                     THEN i.totalAmount * (CASE WHEN i.exchangeRate > 0 THEN i.exchangeRate ELSE 25000.0 END)
                
                WHEN :targetCurrency = 'USD' AND i.currency = 'VND' 
                     THEN i.totalAmount / (CASE WHEN i.exchangeRate > 0 THEN i.exchangeRate ELSE 25000.0 END)
                
                ELSE 0 
            END
        ), 0)
        FROM ProductInvoice i 
        WHERE i.invoiceStatus IN :statuses 
        AND i.createdAt BETWEEN :startDate AND :endDate 
    """)
    BigDecimal sumTotalRevenueConverted(
            @Param("statuses") List<String> statuses,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("targetCurrency") String targetCurrency
    );

    // [FIXED] Áp dụng logic tương tự cho Chart Data
    @Query("""
        SELECT new sunshine_dental_care.dto.huybro_reports.RevenueChartDataDto(
           CAST(i.createdAt AS LocalDate), 
           SUM(
                CASE 
                    WHEN :targetCurrency = i.currency THEN i.totalAmount
                    WHEN :targetCurrency = 'VND' AND i.currency = 'USD' 
                         THEN i.totalAmount * (CASE WHEN i.exchangeRate > 0 THEN i.exchangeRate ELSE 25000.0 END)
                    WHEN :targetCurrency = 'USD' AND i.currency = 'VND' 
                         THEN i.totalAmount / (CASE WHEN i.exchangeRate > 0 THEN i.exchangeRate ELSE 25000.0 END)
                    ELSE 0 
                END
           ),
           COUNT(i)
        )
        FROM ProductInvoice i
        WHERE i.invoiceStatus = 'COMPLETED'
        AND i.createdAt BETWEEN :startDate AND :endDate
        GROUP BY CAST(i.createdAt AS LocalDate)
        ORDER BY CAST(i.createdAt AS LocalDate) ASC
    """)
    List<RevenueChartDataDto> getRevenueChartDataConverted(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("targetCurrency") String targetCurrency
    );

    @Query("SELECT COUNT(i) FROM ProductInvoice i WHERE i.invoiceStatus IN :statuses AND i.createdAt BETWEEN :startDate AND :endDate")
    Long countByStatusAndDate(
            @Param("statuses") List<String> statuses,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}