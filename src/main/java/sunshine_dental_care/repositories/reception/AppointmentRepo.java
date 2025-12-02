package sunshine_dental_care.repositories.reception;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.Appointment;

@Repository
public interface AppointmentRepo extends JpaRepository<Appointment, Integer> {

    @Query("SELECT a FROM Appointment a " +
            "WHERE a.status IN ('CONFIRMED', 'SCHEDULED', 'PENDING') " +
            "AND (" +
            "   (a.doctor.id = :doctorId AND a.endDateTime > :newStart AND a.startDateTime < :newEnd) " + // Check Doctor (Toàn hệ thống)
            "   OR " +
            "   (:roomId IS NOT NULL AND a.room.id = :roomId AND a.endDateTime > :newStart AND a.startDateTime < :newEnd) " + // Check Room (Nếu có chọn phòng)
            ")")
    List<Appointment> findConflictAppointments(
            @Param("doctorId") Integer doctorId,
            @Param("roomId") Integer roomId,
            @Param("newStart") Instant newStart,
            @Param("newEnd") Instant newEnd
    );

    /**
     * Lấy danh sách các lịch hẹn ĐÃ CÓ của bác sĩ trong ngày cụ thể.
     * Dùng để tô đen các ô giờ đã bị người khác đặt.
     * (Không cần lọc theo Clinic, vì bác sĩ bận ở đâu thì cũng là bận).
     */
    @Query("SELECT a FROM Appointment a " +
            "WHERE a.doctor.id = :doctorId " +
            "AND a.status IN ('CONFIRMED', 'SCHEDULED', 'PENDING') " +
            "AND CAST(a.startDateTime AS date) = :date")
    List<Appointment> findBusySlotsByDoctorAndDate(
            @Param("doctorId") Integer doctorId,
            @Param("date") LocalDate date
    );

    /**
     * Lấy tất cả lịch hẹn của một Clinic trong ngày cụ thể.
     * Dùng cho Reception Dashboard.
     * Sắp xếp theo giờ bắt đầu để hiển thị đẹp hơn.
     */
    @Query("SELECT a FROM Appointment a " +
            "WHERE a.clinic.id = :clinicId " +
            "AND CAST(a.startDateTime AS date) = :date " +
            "ORDER BY a.startDateTime ASC")
    List<Appointment> findByClinicIdAndDate(
            @Param("clinicId") Integer clinicId,
            @Param("date") LocalDate date
    );
}
