package sunshine_dental_care.api.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.entities.Holiday;
import sunshine_dental_care.entities.Log;
import sunshine_dental_care.entities.SystemConfig;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.interfaces.system.AuditLogService;
import sunshine_dental_care.services.interfaces.system.SystemConfigService;

@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemController {

    private final SystemConfigService systemConfigService;
    private final AuditLogService auditLogService;

    // --- System Configs ---

    @GetMapping("/configs")
    public ResponseEntity<List<SystemConfig>> getAllConfigs() {
        return ResponseEntity.ok(systemConfigService.getAllConfigs());
    }

    @PutMapping("/configs")
    public ResponseEntity<SystemConfig> updateConfig(
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal CurrentUser currentUser) {
        String key = payload.get("key");
        String value = payload.get("value");
        String description = payload.get("description");

        SystemConfig updated = systemConfigService.updateConfig(key, value, description);

        return ResponseEntity.ok(updated);
    }

    // --- Holidays ---

    @GetMapping("/holidays")
    public ResponseEntity<List<Holiday>> getAllHolidays() {
        return ResponseEntity.ok(systemConfigService.getAllHolidays());
    }

    @PostMapping("/holidays")
    public ResponseEntity<Holiday> addHoliday(@RequestBody Map<String, Object> payload) {
        String name = (String) payload.get("name");
        String dateStr = (String) payload.get("date");
        Boolean isRecurring = (Boolean) payload.get("isRecurring");
        Integer duration = payload.get("duration") != null
                ? (payload.get("duration") instanceof Integer
                        ? (Integer) payload.get("duration")
                        : Integer.valueOf(payload.get("duration").toString()))
                : 1;

        // Extract clinicId from payload
        Integer clinicId = null;
        if (payload.get("clinicId") != null) {
            Object clinicIdObj = payload.get("clinicId");
            clinicId = clinicIdObj instanceof Integer ? (Integer) clinicIdObj : Integer.valueOf(clinicIdObj.toString());
        }

        LocalDate date = LocalDate.parse(dateStr);
        Holiday holiday = systemConfigService.addHoliday(date, name, isRecurring, duration, clinicId);
        return ResponseEntity.ok(holiday);
    }

    @DeleteMapping("/holidays/{id}")
    public ResponseEntity<Void> deleteHoliday(@PathVariable Integer id) {
        systemConfigService.deleteHoliday(id);
        return ResponseEntity.noContent().build();
    }

    // --- Audit Logs ---

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<Log>> getAuditLogs(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<Log> logs = auditLogService.getAuditLogs(userId, action, tableName, fromDate, toDate, page, size);
        return ResponseEntity.ok(logs);
    }
}
