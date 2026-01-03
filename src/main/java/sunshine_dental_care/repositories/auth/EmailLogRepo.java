package sunshine_dental_care.repositories.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.EmailLog;

public interface EmailLogRepo extends JpaRepository<EmailLog,Integer> {
}
