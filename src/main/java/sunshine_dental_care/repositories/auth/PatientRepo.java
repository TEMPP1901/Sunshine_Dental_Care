package sunshine_dental_care.repositories.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sunshine_dental_care.entities.Patient;

import java.util.List;
import java.util.Optional;


public interface PatientRepo extends JpaRepository<Patient, Integer> {
    Optional<Patient> findByUserId(Integer userId);
    Optional<Patient> findByPatientCode(String patientCode);

    Optional<Patient> findFirstByPhone(String phone);

    Optional<Patient> findFirstByEmail(String email);

    List<Patient> findByFullNameContainingIgnoreCase(String fullName);

    @Query("""
        SELECT p
        FROM Patient p
        WHERE (:fullName IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :fullName, '%')))
          AND (:gender IS NULL OR p.gender = :gender)
          AND (:dateOfBirth IS NULL OR p.dateOfBirth = :dateOfBirth)
          AND (:phone IS NULL OR p.phone = :phone)
          AND (:email IS NULL OR LOWER(p.email) = LOWER(:email))
    """)
    List<Patient> searchPatients(
            @Param("fullName") String fullName,
            @Param("gender") String gender,
            @Param("dateOfBirth") java.time.LocalDate dateOfBirth,
            @Param("phone") String phone,
            @Param("email") String email
    );

}
