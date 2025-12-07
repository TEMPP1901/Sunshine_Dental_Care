package sunshine_dental_care.repositories.huybro_products;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sunshine_dental_care.dto.huybro_products.ProductPurchaseHistoryDto;
import sunshine_dental_care.dto.huybro_reports.TopSellingProductDto;
import sunshine_dental_care.entities.huybro_product_invoices.ProductInvoiceItem;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductInvoiceItemRepository extends JpaRepository<ProductInvoiceItem, Integer> {
    // Giúp API load chi tiết hóa đơn cực nhanh
    @Query("SELECT item FROM ProductInvoiceItem item " +
            "LEFT JOIN FETCH item.product " +
            "WHERE item.invoice.id = :invoiceId")
    List<ProductInvoiceItem> findAllByInvoiceIdWithProduct(@Param("invoiceId") Integer invoiceId);

    // Tính tổng số lượng đã bán (Chỉ tính đơn CONFIRMED trở lên để chắc chắn đã trừ kho/chốt đơn)
    @Query("""
        SELECT COALESCE(SUM(item.quantity), 0)
        FROM ProductInvoiceItem item
        WHERE item.product.id = :productId
          AND item.invoice.invoiceStatus IN ('CONFIRMED', 'PROCESSING', 'COMPLETED')
    """)
    Integer sumSoldQuantityByProductId(@Param("productId") Integer productId);

    // Lấy danh sách người mua gần đây (Dùng Pageable để limit số lượng)
    @Query("""
        SELECT new sunshine_dental_care.dto.huybro_products.ProductPurchaseHistoryDto(
            COALESCE(item.invoice.customerFullName, 'Guest'),
            item.unitPriceBeforeTax,
            item.quantity,
            item.createdAt
        )
        FROM ProductInvoiceItem item
        WHERE item.product.id = :productId
          AND item.invoice.invoiceStatus IN ('CONFIRMED', 'PROCESSING', 'COMPLETED')
        ORDER BY item.createdAt DESC
    """)
    List<ProductPurchaseHistoryDto> findRecentPurchases(@Param("productId") Integer productId, PageRequest pageable);
    @Query("""
        SELECT new sunshine_dental_care.dto.huybro_reports.TopSellingProductDto(
           item.productNameSnapshot,
           item.skuSnapshot,
           SUM(item.quantity),
           SUM(
               CASE 
                   WHEN :targetCurrency = item.invoice.currency THEN item.lineTotalAmount
                   
                   WHEN :targetCurrency = 'VND' AND item.invoice.currency = 'USD' 
                        THEN item.lineTotalAmount * (CASE WHEN item.invoice.exchangeRate > 0 THEN item.invoice.exchangeRate ELSE 25000.0 END)
                   
                   WHEN :targetCurrency = 'USD' AND item.invoice.currency = 'VND' 
                        THEN item.lineTotalAmount / (CASE WHEN item.invoice.exchangeRate > 0 THEN item.invoice.exchangeRate ELSE 25000.0 END)
                   
                   ELSE 0 
               END
           )
        )
        FROM ProductInvoiceItem item
        WHERE item.invoice.invoiceStatus = 'COMPLETED'
        AND item.invoice.createdAt BETWEEN :startDate AND :endDate
        GROUP BY item.productNameSnapshot, item.skuSnapshot
        ORDER BY SUM(
             CASE 
                   WHEN :targetCurrency = item.invoice.currency THEN item.lineTotalAmount
                   WHEN :targetCurrency = 'VND' AND item.invoice.currency = 'USD' 
                        THEN item.lineTotalAmount * (CASE WHEN item.invoice.exchangeRate > 0 THEN item.invoice.exchangeRate ELSE 25000.0 END)
                   ELSE 0 
             END
        ) DESC
    """)
    List<TopSellingProductDto> findTopSellingProductsConverted(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("targetCurrency") String targetCurrency,
            Pageable pageable
    );
}
