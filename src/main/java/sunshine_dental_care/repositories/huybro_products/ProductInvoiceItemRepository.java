package sunshine_dental_care.repositories.huybro_products;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sunshine_dental_care.entities.huybro_product_invoices.ProductInvoiceItem;

import java.util.List;

public interface ProductInvoiceItemRepository extends JpaRepository<ProductInvoiceItem, Integer> {
    // Giúp API load chi tiết hóa đơn cực nhanh
    @Query("SELECT item FROM ProductInvoiceItem item " +
            "LEFT JOIN FETCH item.product " +
            "WHERE item.invoice.id = :invoiceId")
    List<ProductInvoiceItem> findAllByInvoiceIdWithProduct(@Param("invoiceId") Integer invoiceId);
}
