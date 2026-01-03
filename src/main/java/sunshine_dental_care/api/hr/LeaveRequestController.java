package sunshine_dental_care.api.hr;

import java.util.List;

import org.springframework.data.domain.Page;
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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.hrDTO.LeaveRequestRequest;
import sunshine_dental_care.dto.hrDTO.LeaveRequestResponse;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.interfaces.hr.LeaveRequestService;

@RestController
@RequestMapping("/api/hr/leave-requests")
@RequiredArgsConstructor
public class LeaveRequestController {

        private final LeaveRequestService leaveRequestService;

        // Nhân viên/bác sĩ tạo đơn xin nghỉ
        // Cho phép: DOCTOR, HR, RECEPTION, ACCOUNTANT, ADMIN
        // Không cho: USER (bệnh nhân)
        @PostMapping
        @PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT', 'ADMIN')")
        public ResponseEntity<LeaveRequestResponse> createLeaveRequest(
                        @AuthenticationPrincipal CurrentUser currentUser,
                        @Valid @RequestBody LeaveRequestRequest request) {
                LeaveRequestResponse response = leaveRequestService.createLeaveRequest(
                                currentUser.userId(), request);
                return ResponseEntity.ok(response);
        }

        // Nhân viên/bác sĩ xem danh sách đơn xin nghỉ của mình
        @GetMapping("/my")
        @PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT', 'ADMIN')")
        public ResponseEntity<List<LeaveRequestResponse>> getMyLeaveRequests(
                        @AuthenticationPrincipal CurrentUser currentUser) {
                List<LeaveRequestResponse> responses = leaveRequestService.getMyLeaveRequests(
                                currentUser.userId());
                return ResponseEntity.ok(responses);
        }

        // Nhân viên/bác sĩ xem đơn xin nghỉ của mình với phân trang
        @GetMapping("/my/paged")
        @PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT', 'ADMIN')")
        public ResponseEntity<Page<LeaveRequestResponse>> getMyLeaveRequestsPaged(
                        @AuthenticationPrincipal CurrentUser currentUser,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size) {
                Page<LeaveRequestResponse> responses = leaveRequestService.getMyLeaveRequests(
                                currentUser.userId(), page, size);
                return ResponseEntity.ok(responses);
        }

        // Nhân viên/bác sĩ xem chi tiết đơn xin nghỉ
        @GetMapping("/{leaveRequestId}")
        @PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT', 'ADMIN')")
        public ResponseEntity<LeaveRequestResponse> getLeaveRequestById(
                        @AuthenticationPrincipal CurrentUser currentUser,
                        @PathVariable Integer leaveRequestId) {
                LeaveRequestResponse response = leaveRequestService.getLeaveRequestById(
                                leaveRequestId, currentUser.userId());
                return ResponseEntity.ok(response);
        }

        // Nhân viên/bác sĩ hủy đơn xin nghỉ
        @DeleteMapping("/{leaveRequestId}")
        @PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT', 'ADMIN')")
        public ResponseEntity<Void> cancelLeaveRequest(
                        @AuthenticationPrincipal CurrentUser currentUser,
                        @PathVariable Integer leaveRequestId) {
                leaveRequestService.cancelLeaveRequest(leaveRequestId, currentUser.userId());
                return ResponseEntity.noContent().build();
        }

        // HR xem tất cả đơn xin nghỉ pending
        // HR xem tất cả đơn xin nghỉ pending
        @GetMapping("/pending")
        @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
        public ResponseEntity<List<LeaveRequestResponse>> getAllPendingLeaveRequests() {
                List<LeaveRequestResponse> responses = leaveRequestService.getAllPendingLeaveRequests();
                return ResponseEntity.ok(responses);
        }

        // HR/Admin xem tất cả đơn xin nghỉ pending admin
        @GetMapping("/pending-admin")
        @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
        public ResponseEntity<List<LeaveRequestResponse>> getAllPendingAdminLeaveRequests() {
                List<LeaveRequestResponse> responses = leaveRequestService.getAllPendingAdminLeaveRequests();
                return ResponseEntity.ok(responses);
        }

        // Lấy số lượng đơn xin nghỉ theo trạng thái
        @GetMapping("/counts")
        @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
        public ResponseEntity<java.util.Map<String, Long>> getLeaveRequestCounts() {
                java.util.Map<String, Long> counts = leaveRequestService.getLeaveRequestCounts();
                return ResponseEntity.ok(counts);
        }

        // Lấy danh sách đơn xin nghỉ đã được duyệt trong khoảng thời gian (cho tạo schedule)
        @GetMapping("/approved-in-range")
        @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
        public ResponseEntity<List<LeaveRequestResponse>> getApprovedLeaveRequestsInRange(
                        @RequestParam String startDate,
                        @RequestParam String endDate) {
                java.time.LocalDate start = java.time.LocalDate.parse(startDate);
                java.time.LocalDate end = java.time.LocalDate.parse(endDate);
                List<LeaveRequestResponse> responses = leaveRequestService.getApprovedLeaveRequestsInDateRange(start, end);
                return ResponseEntity.ok(responses);
        }

        // HR xem tất cả đơn xin nghỉ với phân trang
        @GetMapping
        @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
        public ResponseEntity<Page<LeaveRequestResponse>> getAllLeaveRequests(
                        @RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size) {
                Page<LeaveRequestResponse> responses = leaveRequestService.getAllLeaveRequests(
                                status, page, size);
                return ResponseEntity.ok(responses);
        }

        // HR duyệt/từ chối đơn xin nghỉ
        @PutMapping("/process")
        @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
        public ResponseEntity<LeaveRequestResponse> processLeaveRequest(
                        @AuthenticationPrincipal CurrentUser currentUser,
                        @Valid @RequestBody LeaveRequestRequest request) {
                LeaveRequestResponse response = leaveRequestService.processLeaveRequest(
                                currentUser.userId(), request);
                return ResponseEntity.ok(response);
        }
}
