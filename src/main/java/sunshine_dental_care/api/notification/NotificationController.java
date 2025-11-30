package sunshine_dental_care.api.notification;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.notificationDTO.NotificationRequest;
import sunshine_dental_care.dto.notificationDTO.NotificationResponse;
import sunshine_dental_care.dto.notificationDTO.NotificationStatistics;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.impl.notification.NotificationService;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/device")
    public ResponseEntity<Void> registerDevice(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam String token,
            @RequestParam(defaultValue = "WEB") String deviceType) {
        notificationService.registerDevice(currentUser.userId(), token, deviceType);
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> sendNotification(@RequestBody NotificationRequest request) {
        return ResponseEntity.ok(notificationService.sendNotification(request));
    }

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean includeExpired) {
        return ResponseEntity.ok(notificationService.getNotifications(
                currentUser.userId(), page, size, includeExpired));
    }

    @GetMapping("/unread")
    public ResponseEntity<java.util.List<NotificationResponse>> getUnreadNotifications(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(notificationService.getUnreadNotifications(currentUser.userId()));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable Integer id,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(notificationService.markAsRead(id, currentUser.userId()));
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal CurrentUser currentUser) {
        notificationService.markAllAsRead(currentUser.userId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> countUnread(@AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(notificationService.countUnread(currentUser.userId()));
    }

    @GetMapping("/statistics")
    public ResponseEntity<NotificationStatistics> getStatistics(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(notificationService.getStatistics(currentUser.userId()));
    }

    @GetMapping("/sync")
    public ResponseEntity<java.util.List<NotificationResponse>> syncNotifications(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastSyncTime) {
        return ResponseEntity.ok(notificationService.syncNotifications(
                currentUser.userId(), lastSyncTime));
    }
}
