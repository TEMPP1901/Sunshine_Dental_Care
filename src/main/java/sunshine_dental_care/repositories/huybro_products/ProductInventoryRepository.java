package sunshine_dental_care.repositories.huybro_products;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.dto.huybro_inventories.InventoryViewDto;
import sunshine_dental_care.entities.huybro_product_inventories.ProductInventory;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductInventoryRepository extends JpaRepository<ProductInventory, Integer> {

    // 1. Tìm kho của sản phẩm X tại Chi nhánh Y
    Optional<ProductInventory> findByProductIdAndClinicId(Integer productId, Integer clinicId);

    // 2. Tính tổng số lượng (quantity) của sản phẩm X trên TẤT CẢ chi nhánh
    // Dùng khi: Đồng bộ lại field 'unit' trong bảng Product sau khi nhập/xuất
    // COALESCE(..., 0) để tránh lỗi nếu chưa có record nào thì trả về 0
    @Query("SELECT COALESCE(SUM(pi.quantity), 0) FROM ProductInventory pi WHERE pi.product.id = :productId")
    Integer sumTotalQuantityByProductId(@Param("productId") Integer productId);

    // 3. Lấy danh sách inventory theo sản phẩm (kèm thông tin Clinic)
    @Query("SELECT pi FROM ProductInventory pi JOIN FETCH pi.clinic WHERE pi.product.id = :productId")
    List<ProductInventory> findByProductId(@Param("productId") Integer productId);

    // 4.
    @Query("""
        SELECT new sunshine_dental_care.dto.huybro_inventories.InventoryViewDto(
            pi.id,
            p.id,
            p.sku,
            p.productName,
            (SELECT img.imageUrl FROM ProductImage img WHERE img.product.id = p.id AND img.imageOrder = 1),
            c.id,
            c.clinicName,
            pi.quantity,
            p.defaultRetailPrice,  
            p.currency,            
            pi.minStockLevel,
            pi.lastUpdated
        )
        FROM ProductInventory pi
        JOIN pi.product p
        JOIN pi.clinic c
        WHERE (:clinicId IS NULL OR c.id = :clinicId)
        AND (:keyword IS NULL OR :keyword = '' 
             OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """)
    Page<InventoryViewDto> searchInventory(
            @Param("keyword") String keyword,
            @Param("clinicId") Integer clinicId,
            Pageable pageable
    );
}