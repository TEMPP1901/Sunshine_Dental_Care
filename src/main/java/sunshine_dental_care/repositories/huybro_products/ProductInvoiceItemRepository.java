package sunshine_dental_care.repositories.huybro_products;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.huybro_product_invoice.ProductInvoiceItem;

import java.util.List;

public interface ProductInvoiceItemRepository extends JpaRepository<ProductInvoiceItem, Integer> {

    List<ProductInvoiceItem> findByInvoice_Id(Integer invoiceId);
}
