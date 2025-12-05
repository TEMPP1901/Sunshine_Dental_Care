package sunshine_dental_care.entities;

import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "Holidays")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Holiday {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "isRecurring")
    private Boolean isRecurring; // True if repeats every year

    @Column(name = "duration")
    @Builder.Default
    private Integer duration = 1; // Number of days off

    @Column(name = "clinicId")
    private Integer clinicId; // NULL = All Clinics, Value = Specific Clinic
}
