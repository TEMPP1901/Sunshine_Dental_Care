package sunshine_dental_care.repositories.admin;

import java.time.Instant;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import sunshine_dental_care.entities.Patient;

@org.springframework.stereotype.Repository
public interface AdminPatientStatsRepository extends Repository<Patient, Integer> {

    // Đếm số bệnh nhân tạo mới trong khoảng thời gian chỉ định
    @Query("""
            SELECT COUNT(p) FROM Patient p
            WHERE p.createdAt >= :start AND p.createdAt < :end
            """)
    long countCreatedBetween(
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    // Đếm tổng số bệnh nhân
    @Query("SELECT COUNT(p) FROM Patient p")
    long countAllPatients();
}
