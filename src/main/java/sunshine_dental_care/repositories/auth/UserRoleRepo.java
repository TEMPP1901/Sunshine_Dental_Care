package sunshine_dental_care.repositories.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import sunshine_dental_care.entities.UserRole;

public interface UserRoleRepo extends JpaRepository<UserRole, Integer> {
  @Query("""
        select ur from UserRole ur
        where ur.user.id = :userId and ur.isActive = true
      """)
  List<UserRole> findActiveByUserId(Integer userId);

  @Query("""
        select ur from UserRole ur
        where ur.user.id = :userId
      """)
  List<UserRole> findByUserId(Integer userId);

  @Query("""
        select concat('ROLE_', upper(r.roleName))
        from UserRole ur
        join ur.role r
        where ur.user.id = :userId
          and (ur.isActive = true or ur.isActive is null)
      """)
  List<String> findRoleNamesByUserId(Integer userId);

  @Query("""
        select ur from UserRole ur
        join fetch ur.role
        where ur.user.id IN :userIds and ur.isActive = true
      """)
  List<UserRole> findActiveByUserIdIn(List<Integer> userIds);

  @Query("""
        select ur from UserRole ur
        join fetch ur.role r
        where r.roleName = :roleName and ur.isActive = true
      """)
  List<UserRole> findByRoleName(String roleName);

  @Query("""
        select distinct ur.user.id
        from UserRole ur
        join ur.role r
        where lower(r.roleName) = lower(:roleName)
          and ur.isActive = true
          and ur.user.isActive = true
      """)
  List<Integer> findUserIdsByRoleName(String roleName);
}
