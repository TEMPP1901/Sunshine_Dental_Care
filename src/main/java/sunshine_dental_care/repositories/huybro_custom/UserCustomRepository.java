package sunshine_dental_care.repositories.huybro_custom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.dto.huybro_payroll.UserSearchResponse; // Import DTO mới
import sunshine_dental_care.entities.User;

import java.util.List;

@Repository
public interface UserCustomRepository extends JpaRepository<User, Integer> {

    /**
     * Sử dụng: SELECT new ... UserSearchResponse(...)
     */
    @Query("SELECT DISTINCT new sunshine_dental_care.dto.huybro_payroll.UserSearchResponse(" +
            "   u.id, " +
            "   u.fullName, " +
            "   u.email, " +
            "   u.code, " +
            "   r.roleName " +
            ") " +
            "FROM User u " +
            "JOIN u.userRoles ur " +
            "JOIN ur.role r " +
            "WHERE ur.isActive = true " +
            "AND r.roleName IN :roles " +
            "AND ( " +
            "   LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(u.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            ")")
    List<UserSearchResponse> searchStaff(@Param("roles") List<String> roles, @Param("keyword") String keyword);

    @Query("SELECT new sunshine_dental_care.dto.huybro_payroll.UserSearchResponse(" +
            "   u.id, " +
            "   u.fullName, " +
            "   u.email, " +
            "   u.code, " +
            "   r.roleName " +
            ") " +
            "FROM User u " +
            "JOIN u.userRoles ur JOIN ur.role r " +
            "WHERE u.isActive = true " +
            "AND r.roleName IN :roles " +
            "AND u.id NOT IN (SELECT sp.user.id FROM SalaryProfile sp)")
    List<UserSearchResponse> findMissingSalaryProfiles(@Param("roles") List<String> roles);
}