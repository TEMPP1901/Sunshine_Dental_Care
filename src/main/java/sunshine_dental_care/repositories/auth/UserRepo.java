package sunshine_dental_care.repositories.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.User;

import java.util.Optional;

public interface UserRepo extends JpaRepository<User, Integer> {
    Optional <User> findByUsernameIgnoreCase(String email);
}
