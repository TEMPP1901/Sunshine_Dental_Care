package sunshine_dental_care.entities.huypro_products;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ProductTypes")
public class ProductType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "typeId", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "typeName", nullable = false, length = 100)
    private String typeName;

    @Nationalized
    @Column(name = "typeDescription", nullable = false, length = 500)
    private String typeDescription;

    @Nationalized
    @Column(name = "examples", nullable = false, length = 500)
    private String examples;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

}