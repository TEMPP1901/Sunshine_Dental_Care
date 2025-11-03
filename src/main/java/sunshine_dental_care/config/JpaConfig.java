// Bật tính năng JPA Auditing để tự động ghi createdAt, updatedAt, v.v. cho entity
package sunshine_dental_care.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
