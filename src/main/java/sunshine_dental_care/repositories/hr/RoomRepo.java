package sunshine_dental_care.repositories.hr;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.Room;

@Repository
public interface RoomRepo extends JpaRepository<Room, Integer> {
    
    // Lấy tất cả rooms active theo thứ tự tên
    List<Room> findByIsActiveTrueOrderByRoomNameAsc();
    
    // Lấy rooms theo clinic
    List<Room> findByClinicIdAndIsActiveTrueOrderByRoomNameAsc(Integer clinicId);
}

