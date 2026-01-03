package sunshine_dental_care.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.AIRecommendation;

@Repository
public interface AIRecommendationRepository extends JpaRepository<AIRecommendation, Integer> {
    // Lấy lời khuyên mới nhất của bệnh nhân
    @Query(value = "SELECT TOP 1 * FROM AIRecommendations WHERE patientId = ?1 ORDER BY suggestedAt DESC", nativeQuery = true)
    AIRecommendation findLatestByPatientId(Integer patientId);
}