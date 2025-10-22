package sunshine_dental_care.repositories.huybro_products;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import sunshine_dental_care.entities.huybro_products.Product;

public interface ProductRepository extends JpaRepository<Product, Integer>, JpaSpecificationExecutor<Product> {
    // 23:31 21/10/2025 nghiệp vụ view 1 và nhiều sản phẩm chưa cần thêm phương thức
}
