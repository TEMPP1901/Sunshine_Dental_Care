package sunshine_dental_care.repositories.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.User;

@Repository
public interface UserRepo extends JpaRepository<User, Integer> {

    // --- PHẦN CHUNG ---
    Optional<User> findByEmailIgnoreCase(String email);

    // [FIX LỖI] Thêm dòng này để khớp với PatientService
    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameIgnoreCase(String username);

    @Query("SELECT u FROM User u WHERE UPPER(u.code) = UPPER(:code)")
    Optional<User> findByCodeIgnoreCase(@Param("code") String code);

    Optional<User> findByResetPasswordToken(String token);

    // --- PHẦN CỦA TUẤN (Auth) ---
    Optional<User> findByVerificationToken(String token);

    // [QUAN TRỌNG] Login bằng SĐT
    Optional<User> findByPhone(String phone);

    // --- PHẦN CỦA LONG (Doctor Logic) ---
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.doctorSpecialties WHERE u.id = :userId")
    Optional<User> findByIdWithSpecialties(@Param("userId") Integer userId);

    // Hàm lấy Bác sĩ theo Clinic (Không lọc chuyên khoa)
    @Query("SELECT u FROM User u JOIN u.userRoles ur " +
            "WHERE ur.role.id = 3 " +
            "AND u.isActive = true " +
            "AND EXISTS (SELECT uca FROM UserClinicAssignment uca WHERE uca.user.id = u.id AND uca.clinic.id = :clinicId)")
    List<User> findDoctorsByClinicId(@Param("clinicId") Integer clinicId);

    // Hàm lấy Bác sĩ theo Clinic và DANH SÁCH Chuyên khoa
    @Query("SELECT DISTINCT u FROM User u " +
            "JOIN u.userRoles ur " +
            "WHERE ur.role.id = 3 " + // Role Doctor
            "AND u.isActive = true " +
            "AND EXISTS (SELECT s FROM DoctorSchedule s WHERE s.doctor.id = u.id AND s.clinic.id = :clinicId) " +
            "AND (SELECT COUNT(DISTINCT ds.specialtyName) FROM DoctorSpecialty ds " +
            "     WHERE ds.doctor.id = u.id " +
            "     AND ds.specialtyName IN :specialties " +
            "     AND ds.isActive = true) >= :count")
    List<User> findDoctorsBySpecialties(
            @Param("clinicId") Integer clinicId,
            @Param("specialties") List<String> specialties,
            @Param("count") Long count
    );
}