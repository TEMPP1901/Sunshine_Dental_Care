package sunshine_dental_care.entities.huybro_salary;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;
import sunshine_dental_care.entities.huybro_salary.enums.AllowanceType;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "salary_allowances")
public class SalaryAllowance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salary_profile_id", nullable = false)
    private SalaryProfile salaryProfile;

    @Nationalized
    @Column(name = "allowance_name", nullable = false, length = 100)
    private String allowanceName;

    @ColumnDefault("0")
    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AllowanceType type;

    @Nationalized
    @Column(name = "note", nullable = false)
    private String note;
}