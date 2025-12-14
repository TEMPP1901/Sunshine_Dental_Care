package sunshine_dental_care.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "Invoices")
@Getter
@Setter
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoiceId", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patientId")
    private Patient patient;

    @Column(name = "totalAmount")
    private BigDecimal totalAmount;

    @Column(name = "paymentStatus")
    private String paymentStatus; // PAID, PENDING...

    // ... (Các trường khác nếu cần, nhưng 3 trường trên là quan trọng nhất để tính điểm)
}