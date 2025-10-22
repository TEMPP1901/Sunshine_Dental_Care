package sunshine_dental_care.repositories.huybro_products;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.huybro_products.ProductsProductType;
import sunshine_dental_care.entities.huybro_products.ProductsProductTypeId;
import java.util.List;

public interface ProductsProductTypeRepository extends JpaRepository<ProductsProductType, ProductsProductTypeId> {
    // Lấy danh sách loại sản phẩm của 1 sản phẩm cho view 1 và nhiều sản phẩm
    List<ProductsProductType> findByProduct_Id(Integer productId);
}
