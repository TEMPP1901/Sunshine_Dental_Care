package sunshine_dental_care.repositories.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.Role;

import java.util.Optional;

public interface RoleRepo extends JpaRepository<Role, String> {
    Optional<Role> findByRoleName(String roleName);
}
