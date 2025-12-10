package sunshine_dental_care.repositories.system;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.Holiday;

@Repository
public interface HolidayRepo extends JpaRepository<Holiday, Integer> {
    List<Holiday> findByDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("SELECT h FROM Holiday h WHERE h.date = :date OR (h.isRecurring = true AND MONTH(h.date) = MONTH(:date) AND DAY(h.date) = DAY(:date))")
    List<Holiday> findHolidaysForDate(LocalDate date);
}
