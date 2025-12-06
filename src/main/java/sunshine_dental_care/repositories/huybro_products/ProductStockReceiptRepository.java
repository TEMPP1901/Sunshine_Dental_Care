package sunshine_dental_care.repositories.huybro_products;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.huybro_product_inventories.ProductStockReceipt;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface ProductStockReceiptRepository extends JpaRepository<ProductStockReceipt, Integer> {
    @Query(value = "SELECT TOP 1 * FROM ProductStockReceipts WHERE productId = :productId ORDER BY createdAt DESC", nativeQuery = true)
    Optional<ProductStockReceipt> findTopByProduct_IdOrderByCreatedAtDesc(@Param("productId") Integer productId);

    Page<ProductStockReceipt> findByProductId(Integer productId, Pageable pageable);

    @Query("SELECT MAX(r.newRetailPrice) FROM ProductStockReceipt r WHERE r.product.id = :productId")
    BigDecimal findMaxRetailPriceByProductId(@Param("productId") Integer productId);
}