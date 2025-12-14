package sunshine_dental_care.repositories.huybro_products;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sunshine_dental_care.entities.huybro_products.Product;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    @Query(
            value = """
            SELECT DISTINCT p
            FROM Product p
            LEFT JOIN ProductsProductType ppt ON ppt.product = p
            LEFT JOIN ppt.type t
            WHERE (:keyword IS NULL OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:active IS NULL OR p.isActive = :active)
              AND (:minPrice IS NULL OR p.defaultRetailPrice >= :minPrice)
              AND (:maxPrice IS NULL OR p.defaultRetailPrice <= :maxPrice)
              AND (:brandEnabled = false OR p.brand IN :brands)
              AND (:typeEnabled  = false OR t.typeName IN :types)
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p.id)
            FROM Product p
            LEFT JOIN ProductsProductType ppt ON ppt.product = p
            LEFT JOIN ppt.type t
            WHERE (:keyword IS NULL OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:active IS NULL OR p.isActive = :active)
              AND (:minPrice IS NULL OR p.defaultRetailPrice >= :minPrice)
              AND (:maxPrice IS NULL OR p.defaultRetailPrice <= :maxPrice)
              AND (:brandEnabled = false OR p.brand IN :brands)
              AND (:typeEnabled  = false OR t.typeName IN :types)
            """
    )
    Page<Product> searchProducts(
            @Param("keyword") String keyword,
            @Param("brands") List<String> brands,
            @Param("brandEnabled") boolean brandEnabled,
            @Param("types") List<String> types,
            @Param("typeEnabled") boolean typeEnabled,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("active") Boolean active,
            Pageable pageable
    );

    @Query("SELECT COALESCE(MAX(p.id), 0) FROM Product p")
    Integer findMaxId();
    List<Product> findByIsActiveTrue();
    
    // Đếm số sản phẩm đang hoạt động
    long countByIsActiveTrue();
}
