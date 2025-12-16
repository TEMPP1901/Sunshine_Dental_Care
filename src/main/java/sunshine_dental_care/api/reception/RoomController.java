package sunshine_dental_care.api.reception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sunshine_dental_care.services.impl.reception.RoomService;

import java.time.Instant;

@RestController
@RequestMapping("/api/reception/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final RoomService roomService;

    /**
     * API: Lấy danh sách phòng TRỐNG (Available)
     * URL: GET /api/reception/rooms/available?clinicId=1&start=2023-10-25T09:00:00Z&end=2023-10-25T10:00:00Z
     */

    @GetMapping("/available")
    // Chỉ cho phép Lễ tân và Admin được gọi
    @PreAuthorize("hasAnyRole('RECEPTION', 'ADMIN')")
    public ResponseEntity<?> getAvailableRooms(
            @RequestParam Integer clinicId,
            @RequestParam Instant start,
            @RequestParam Instant end) {

        // Gọi xuống Service để lọc phòng
        var rooms = roomService.getAvailableRooms(clinicId, start, end);

        if (rooms.isEmpty()) {
            return ResponseEntity.ok(rooms);
        }
        return ResponseEntity.ok(rooms);
    }

    /**
     * API: Lấy tất cả phòng của cơ sở (Dùng cho dropdown filter nếu cần)
     * URL: GET /api/reception/rooms/all?clinicId=1
     */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('RECEPTION', 'ADMIN')")
    public ResponseEntity<?> getAllRooms(@RequestParam Integer clinicId) {
        return ResponseEntity.ok(roomService.getAllActiveRoomsByClinic(clinicId));
    }
}
