package sunshine_dental_care.repositories.hr;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.Room;

@Repository
public interface RoomRepo extends JpaRepository<Room, Integer> {
    
    // Lấy tất cả rooms active theo thứ tự tên
    List<Room> findByIsActiveTrueOrderByRoomNameAsc();
    
    // Lấy rooms theo clinic
    List<Room> findByClinicIdAndIsActiveTrueOrderByRoomNameAsc(Integer clinicId);

    // Logic: Chọn tất cả phòng thuộc Clinic đó
    // TRỪ ĐI (NOT IN) những phòng đang dính lịch hẹn trong khung giờ start -> end
    @Query("SELECT r FROM Room r " +
            "WHERE r.clinic.id = :clinicId " +
            "AND r.isActive = true " +
            "AND r.id NOT IN (" +
            "SELECT a.room.id FROM Appointment a " +
            "WHERE a.clinic.id = :clinicId " +
            "AND a.room IS NOT NULL " +
            // Chỉ loại bỏ các lịch chưa hủy.
            // (CANCELLED, REJECTED = coi như không chiếm phòng)
            "AND a.status NOT IN ('CANCELLED', 'REJECTED') " +
            // Logic trùng giờ: (StartA < EndB) VÀ (EndA > StartB)
            "AND (a.startDateTime < :end AND a.endDateTime > :start)" +
            ") " +
            "ORDER BY r.roomName ASC")
    List<Room> findAvailableRooms(@Param("clinicId") Integer clinicId,
                                  @Param("start") Instant start,
                                  @Param("end") Instant end);
}

