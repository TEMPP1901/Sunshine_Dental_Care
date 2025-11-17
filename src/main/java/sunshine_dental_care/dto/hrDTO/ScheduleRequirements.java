package sunshine_dental_care.dto.hrDTO;

import java.util.HashSet;
import java.util.Set;

/**
 * DTO to hold schedule requirements parsed from description.
 * Contains clinic IDs, days, specialties to cover, and shift preferences.
 */
public class ScheduleRequirements {
    private final Set<Integer> clinicIds;
    private final Set<String> days;
    private final Set<String> specialtiesToCover;
    private final boolean hasMorning;
    private final boolean hasAfternoon;
    private final boolean bothClinicsActive;
    private final Set<Integer> doctorsToExclude; // Bác sĩ nghỉ (không được assign)
    
    public ScheduleRequirements(
        Set<Integer> clinicIds,
        Set<String> days,
        Set<String> specialtiesToCover,
        boolean hasMorning,
        boolean hasAfternoon,
        boolean bothClinicsActive,
        Set<Integer> doctorsToExclude
    ) {
        this.clinicIds = clinicIds;
        this.days = days;
        this.specialtiesToCover = specialtiesToCover;
        this.hasMorning = hasMorning;
        this.hasAfternoon = hasAfternoon;
        this.bothClinicsActive = bothClinicsActive;
        this.doctorsToExclude = doctorsToExclude != null ? doctorsToExclude : new HashSet<>();
    }
    
    public Set<Integer> getClinicIds() {
        return clinicIds;
    }
    
    public Set<String> getDays() {
        return days;
    }
    
    public Set<String> getSpecialtiesToCover() {
        return specialtiesToCover;
    }
    
    public boolean hasMorning() {
        return hasMorning;
    }
    
    public boolean hasAfternoon() {
        return hasAfternoon;
    }
    
    public boolean isBothClinicsActive() {
        return bothClinicsActive;
    }
    
    public Set<Integer> getDoctorsToExclude() {
        return doctorsToExclude;
    }
}

