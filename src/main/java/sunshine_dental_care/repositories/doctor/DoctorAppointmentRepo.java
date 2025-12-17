package sunshine_dental_care.repositories.doctor;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.Appointment;

import java.time.Instant;
import java.util.List;

@Repository
public interface DoctorAppointmentRepo extends JpaRepository<Appointment, Integer> {
    // get appointment by id doctor - JOIN FETCH appointmentServices để load service và variant từ AppointmentService
    @Query("SELECT DISTINCT a FROM Appointment a " +
           "LEFT JOIN FETCH a.appointmentServices aps " +
           "LEFT JOIN FETCH aps.service " +
           "LEFT JOIN FETCH aps.serviceVariant " +
           "WHERE a.doctor.id = :doctorId")
    List<Appointment> findByDoctorId(@Param("doctorId") Integer doctorId);

    // get appointment by id doctor and status - JOIN FETCH appointmentServices
    @Query("SELECT DISTINCT a FROM Appointment a " +
           "LEFT JOIN FETCH a.appointmentServices aps " +
           "LEFT JOIN FETCH aps.service " +
           "LEFT JOIN FETCH aps.serviceVariant " +
           "WHERE a.doctor.id = :doctorId AND a.status = :status")
    List<Appointment> findByDoctorIdAndStatus(@Param("doctorId") Integer doctorId, @Param("status") String status);

    //get detail appointment by id - JOIN FETCH appointmentServices
    @Query("SELECT DISTINCT a FROM Appointment a " +
           "LEFT JOIN FETCH a.appointmentServices aps " +
           "LEFT JOIN FETCH aps.service " +
           "LEFT JOIN FETCH aps.serviceVariant " +
           "WHERE a.id = :appointmentId AND a.doctor.id = :doctorId")
    Appointment findByIdAndDoctorId(@Param("appointmentId") Integer appointmentId, @Param("doctorId") Integer doctorId);

    //change status appointment
    @Modifying
    @Transactional
    @Query("UPDATE Appointment a SET a.status = :status WHERE a.id = :appointmentId")
    void updateStatus(@Param("appointmentId") Integer appointmentId,
                      @Param("status") String status);

    // get all appointment by id doctor and date range - JOIN FETCH appointmentServices
    @Query("SELECT DISTINCT a FROM Appointment a " +
           "LEFT JOIN FETCH a.appointmentServices aps " +
           "LEFT JOIN FETCH aps.service " +
           "LEFT JOIN FETCH aps.serviceVariant " +
           "WHERE a.doctor.id = :doctorId AND a.startDateTime BETWEEN :startDate AND :endDate")
    List<Appointment> findByDoctorIdAndStartDateTimeBetween(@Param("doctorId") Integer doctorId, 
                                                             @Param("startDate") Instant startDate, 
                                                             @Param("endDate") Instant endDate);
}
