package sunshine_dental_care.repositories.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sunshine_dental_care.entities.EmailTemplate;

import java.util.Optional;

public interface EmailTemplateRepo extends JpaRepository<EmailTemplate, Integer> {
    @Query("select t from EmailTemplate t where t.key = :key and t.locale = :locale and t.isActive = true")
    Optional<EmailTemplate> findActiveByKeyAndLocale(String key, String locale);
}
