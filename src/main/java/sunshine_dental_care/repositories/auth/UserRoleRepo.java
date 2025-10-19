package sunshine_dental_care.repositories.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sunshine_dental_care.entities.UserRole;

import java.util.List;

public interface UserRoleRepo extends JpaRepository<UserRole, Integer> {
    @Query("""
      select ur from UserRole ur
      where ur.user.id = :userId and ur.isActive = true
    """)
    List<UserRole> findActiveByUserId(Integer userId);
}
