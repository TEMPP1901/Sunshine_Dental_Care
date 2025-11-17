package sunshine_dental_care.repositories.huybro_products;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.huybro_products.ProductType;

public interface ProductTypeRepository extends JpaRepository<ProductType, Integer> {
    // 23:31 21/10/2025 nghiệp vụ view 1 và nhiều sản phẩm chưa cần thêm phương thức
}
