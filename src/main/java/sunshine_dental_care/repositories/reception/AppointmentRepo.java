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

    // --- 1. PHẦN CỦA BẠN: LẤY LỊCH SỬ KHÁM BỆNH NHÂN ---
    List<Appointment> findByPatientIdOrderByStartDateTimeDesc(Integer patientId);

    // --- 2. PHẦN CHUNG (LOGIC TÌM XUNG ĐỘT & SLOT TRỐNG) ---
    // (Dùng bản của Long vì có comment rõ ràng, logic SQL giống hệt nhau)

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
     */
    @Query("SELECT a FROM Appointment a " +
            "WHERE a.clinic.id = :clinicId " +
            "AND CAST(a.startDateTime AS date) = :date " +
            "ORDER BY a.startDateTime ASC")
    List<Appointment> findByClinicIdAndDate(
            @Param("clinicId") Integer clinicId,
            @Param("date") LocalDate date
    );

    // --- 3. PHẦN CỦA LONG (MỚI THÊM) ---
    // Lấy TẤT CẢ lịch hẹn trong ngày (không filter theo clinic).
    @Query("SELECT a FROM Appointment a " +
            "WHERE CAST(a.startDateTime AS date) = :date " +
            "ORDER BY a.startDateTime ASC")
    List<Appointment> findAllByDate(
            @Param("date") LocalDate date
    );

    // --- 4. PHẦN CỦA BẠN: LOGIC NHẮC LỊCH (SCHEDULER) ---

    // Query nhắc 24h (Cũ)
    @Query("SELECT a FROM Appointment a WHERE a.status = 'CONFIRMED' " +
            "AND (a.isReminderSent IS NULL OR a.isReminderSent = false) " +
            "AND a.startDateTime BETWEEN :start AND :end")
    List<Appointment> findAppointmentsToRemind(@Param("start") Instant start,
                                               @Param("end") Instant end);

    // Query nhắc gấp 2h (Check cột isUrgentReminderSent)
    @Query("SELECT a FROM Appointment a WHERE a.status = 'CONFIRMED' " +
            "AND (a.isUrgentReminderSent IS NULL OR a.isUrgentReminderSent = false) " +
            "AND a.startDateTime BETWEEN :start AND :end")
    List<Appointment> findUrgentAppointmentsToRemind(@Param("start") Instant start,
                                                     @Param("end") Instant end);

    // --- 5. PHẦN CỦA BẠN: DASHBOARD BỆNH NHÂN ---

    // Lấy lịch hẹn sắp tới (Chưa diễn ra) để hiển thị Countdown
    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId AND a.startDateTime > CURRENT_TIMESTAMP AND a.status IN ('CONFIRMED', 'PENDING', 'SCHEDULED') ORDER BY a.startDateTime ASC")
    List<Appointment> findUpcomingAppointmentsByPatient(@Param("patientId") Integer patientId);

    // Đếm số lịch hẹn đã hoàn thành để tính điểm sức khỏe
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.patient.id = :patientId AND a.status = 'COMPLETED'")
    long countCompletedAppointments(@Param("patientId") Integer patientId);
}