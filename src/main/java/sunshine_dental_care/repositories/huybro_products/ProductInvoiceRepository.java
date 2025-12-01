package sunshine_dental_care.repositories.huybro_products;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.huybro_product_invoice.ProductInvoice;

import java.util.Optional;

public interface ProductInvoiceRepository extends JpaRepository<ProductInvoice, Integer> {

    Optional<ProductInvoice> findByInvoiceCode(String invoiceCode);
}
