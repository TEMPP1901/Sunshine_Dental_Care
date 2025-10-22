package sunshine_dental_care.repositories.huybro_products;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.huybro_products.ProductImage;
import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Integer> {
    // Lấy tất cả ảnh của 1 sản phẩm cho nghiệp vụ view 1 và nhiều sản phẩm
    List<ProductImage> findByProduct_Id(Integer productId);
}
