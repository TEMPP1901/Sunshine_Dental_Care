package sunshine_dental_care.dto.hrDTO;

import java.util.List;
import java.util.Map;

import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.Room;
import sunshine_dental_care.entities.User;

/**
 * DTO to hold all data needed for rule-based schedule generation.
 * Contains doctors, clinics, rooms, and doctors grouped by specialty.
 */
public class RuleBasedScheduleData {
    private final List<User> allDoctors;
    private final List<Clinic> allClinics;
    private final List<Room> allRooms;
    private final Map<String, List<User>> doctorsBySpecialty;
    
    public RuleBasedScheduleData(
        List<User> allDoctors,
        List<Clinic> allClinics,
        List<Room> allRooms,
        Map<String, List<User>> doctorsBySpecialty
    ) {
        this.allDoctors = allDoctors;
        this.allClinics = allClinics;
        this.allRooms = allRooms;
        this.doctorsBySpecialty = doctorsBySpecialty;
    }
    
    public List<User> getAllDoctors() {
        return allDoctors;
    }
    
    public List<Clinic> getAllClinics() {
        return allClinics;
    }
    
    public List<Room> getAllRooms() {
        return allRooms;
    }
    
    public Map<String, List<User>> getDoctorsBySpecialty() {
        return doctorsBySpecialty;
    }
}

