package sunshine_dental_care.repositories.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sunshine_dental_care.entities.PatientSequence;

import java.util.Optional;

public interface PatientSequenceRepo extends JpaRepository<PatientSequence, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PatientSequence s where s.id = :clinicId")
    Optional<PatientSequence> lockByClinicId(@Param("clinicId") Integer clinicId);
}
