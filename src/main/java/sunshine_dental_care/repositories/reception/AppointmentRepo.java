package sunshine_dental_care.repositories.reception;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import sunshine_dental_care.entities.Appointment;

@Repository
public interface AppointmentRepo extends JpaRepository<Appointment, Integer> {

    // --- 1. PHẦN CỦA BẠN: LẤY LỊCH SỬ KHÁM BỆNH NHÂN ---
    List<Appointment> findByPatientIdOrderByStartDateTimeDesc(Integer patientId);

    // --- 2. PHẦN CHUNG (LOGIC TÌM XUNG ĐỘT & SLOT TRỐNG) ---
    // (Dùng bản của Long vì có comment rõ ràng, logic SQL giống hệt nhau)

    @Query("SELECT a FROM Appointment a " +
            "WHERE a.status NOT IN ('CANCELLED', 'REJECTED') " +

            "AND (" +
            "   (a.doctor.id = :doctorId AND a.endDateTime > :newStart AND a.startDateTime < :newEnd) " +
            "   OR " +
            "   (:roomId IS NOT NULL AND a.room.id = :roomId AND a.endDateTime > :newStart AND a.startDateTime < :newEnd) " +
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

    // Check xem phòng có đang bị chiếm trong khung giờ đó không (trừ chính lịch hẹn hiện tại ra)
    @Query("SELECT COUNT(a) > 0 FROM Appointment a " +
            "WHERE a.room.id = :roomId " +
            "AND a.id <> :appointmentId " + // Trừ chính nó (để update phòng khác cho chính nó ko bị lỗi)
            "AND a.status NOT IN ('CANCELLED', 'REJECTED') " +
            "AND (a.startDateTime < :end AND a.endDateTime > :start)")
    boolean existsByRoomIdAndDateOverlap(@Param("roomId") Integer roomId,
                                         @Param("appointmentId") Integer appointmentId,
                                         @Param("start") Instant start,
                                         @Param("end") Instant end);

    // Update trực tiếp các lịch hẹn quá hạn thanh toán từ trạng thái AWAITING_PAYMENT sang CANCELLED
    @Modifying
    @Transactional
    @Query("UPDATE Appointment a SET a.status = :newStatus " +
            "WHERE a.status = :oldStatus AND a.createdAt < :expiryTime")
    int cancelExpiredAppointments(String oldStatus, String newStatus, Instant expiryTime);

    //  HÀM ĐỂ SEARCH DANH SÁCH LỊCH HẸN
    @Query("SELECT a FROM Appointment a WHERE " +
            "(:clinicId IS NULL OR a.clinic.id = :clinicId) " +
            "AND (:keyword IS NULL OR :keyword = '' OR a.patient.fullName LIKE %:keyword% OR a.patient.phone LIKE %:keyword% OR a.patient.patientCode LIKE %:keyword%) " +
            "AND (:paymentStatus IS NULL OR :paymentStatus = '' OR a.paymentStatus = :paymentStatus) " +
            "AND (:status IS NULL OR :status = '' OR a.status = :status) " +
            "AND (:date IS NULL OR CAST(a.startDateTime AS LocalDate) = :date)")
    Page<Appointment> searchAppointments(
            @Param("clinicId") Integer clinicId,
            @Param("keyword") String keyword,
            @Param("paymentStatus") String paymentStatus, // UNPAID, PAID...
            @Param("status") String status,               // SCHEDULED, COMPLETED...
            @Param("date") LocalDate date,                // Có thể null nếu muốn xem tất cả
            Pageable pageable
    );

    // Tìm tất cả lịch hẹn của 1 bệnh nhân
    List<Appointment> findByPatientId(Integer patientId);
}
