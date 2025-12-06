package sunshine_dental_care.repositories.huybro_products;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sunshine_dental_care.dto.huybro_products.ProductPurchaseHistoryDto;
import sunshine_dental_care.entities.huybro_product_invoices.ProductInvoiceItem;

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
}
