package sunshine_dental_care.repositories.huybro_products;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.huybro_product_invoices.ProductInvoice;

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
}