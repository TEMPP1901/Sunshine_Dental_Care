package sunshine_dental_care.repositories.reception;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.Invoice;
import java.math.BigDecimal;

@Repository
public interface InvoiceRepo extends JpaRepository<Invoice, Integer> {
    @Query("SELECT SUM(i.totalAmount) FROM Invoice i WHERE i.patient.id = :patientId AND i.paymentStatus = 'PAID'")
    BigDecimal calculateTotalPaidByPatient(@Param("patientId") Integer patientId);
}