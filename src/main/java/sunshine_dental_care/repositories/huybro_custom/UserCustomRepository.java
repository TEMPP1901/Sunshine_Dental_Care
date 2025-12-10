package sunshine_dental_care.repositories.huybro_custom;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.User;

public interface UserCustomRepository extends JpaRepository<User, Integer> {
}
