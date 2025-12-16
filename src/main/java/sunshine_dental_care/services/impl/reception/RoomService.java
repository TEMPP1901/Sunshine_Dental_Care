package sunshine_dental_care.services.impl.reception;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sunshine_dental_care.entities.Room;
import sunshine_dental_care.exceptions.reception.ResourceNotFoundException;
import sunshine_dental_care.repositories.hr.RoomRepo;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final RoomRepo roomRepo;

    /**
     * 1: Lấy danh sách phòng TRỐNG (Available) trong khung giờ cụ thể
     * Tính năng "Xếp phòng" (Dynamic Room Allocation)
     */
    public List<Room> getAvailableRooms(Integer clinicId, Instant start, Instant end){
        return roomRepo.findAvailableRooms(clinicId, start, end);
    }

    /**
     * Lấy tất cả phòng hoạt động của 1 cơ sở
     * Dùng cho Dropdown filter hoặc màn hình quản lý chung
     */
    public List<Room> getAllActiveRoomsByClinic(Integer clinicId){
        return roomRepo.findByClinicIdAndIsActiveTrueOrderByRoomNameAsc(clinicId);
    }

    /**
     * Lấy chi tiết 1 phòng theo ID
     * Dùng khi cần validate hoặc lấy tên phòng hiển thị
     */
    public Room getRoomById(Integer roomId) {
        return roomRepo.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));
    }
}
