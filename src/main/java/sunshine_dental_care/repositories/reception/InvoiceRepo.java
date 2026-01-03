package sunshine_dental_care.repositories.reception;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.Appointment;

@Repository
public interface InvoiceRepo extends JpaRepository<Appointment, Integer> {
    @Query("SELECT COALESCE(SUM(a.totalAmount), 0) FROM Appointment a WHERE a.patient.id = :patientId AND a.paymentStatus = 'PAID'")
    BigDecimal calculateTotalPaidByPatient(@Param("patientId") Integer patientId);
}
