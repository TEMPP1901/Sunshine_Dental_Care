package sunshine_dental_care.entities.huypro_products;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ProductsProductTypeId implements Serializable {
    private static final long serialVersionUID = -2452325994496011668L;
    @Column(name = "productId", nullable = false)
    private Integer productId;

    @Column(name = "typeId", nullable = false)
    private Integer typeId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ProductsProductTypeId entity = (ProductsProductTypeId) o;
        return Objects.equals(this.productId, entity.productId) &&
                Objects.equals(this.typeId, entity.typeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, typeId);
    }

}