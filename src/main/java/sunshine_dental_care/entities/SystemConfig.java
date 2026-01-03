package sunshine_dental_care.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "SystemConfigs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "configKey", nullable = false, unique = true, length = 100)
    private String configKey;

    @Column(name = "configValue", length = 500)
    private String configValue;

    @Column(name = "description", length = 500)
    private String description;
}
