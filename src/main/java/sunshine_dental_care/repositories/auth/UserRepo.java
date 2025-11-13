package sunshine_dental_care.repositories.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sunshine_dental_care.entities.User;

public interface UserRepo extends JpaRepository<User, Integer> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByUsernameIgnoreCase(String username);
}
