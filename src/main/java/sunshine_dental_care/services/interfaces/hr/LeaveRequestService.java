package sunshine_dental_care.services.interfaces.hr;

import java.util.List;

import org.springframework.data.domain.Page;

import sunshine_dental_care.dto.hrDTO.LeaveRequestRequest;
import sunshine_dental_care.dto.hrDTO.LeaveRequestResponse;

public interface LeaveRequestService {

    // Nhân viên/bác sĩ tạo đơn xin nghỉ
    LeaveRequestResponse createLeaveRequest(Integer userId, LeaveRequestRequest request);

    // Nhân viên/bác sĩ xem danh sách đơn xin nghỉ của mình
    List<LeaveRequestResponse> getMyLeaveRequests(Integer userId);

    // Nhân viên/bác sĩ xem đơn xin nghỉ của mình với phân trang
    Page<LeaveRequestResponse> getMyLeaveRequests(Integer userId, int page, int size);

    // Nhân viên/bác sĩ xem chi tiết đơn xin nghỉ
    LeaveRequestResponse getLeaveRequestById(Integer leaveRequestId, Integer userId);

    // Nhân viên/bác sĩ hủy đơn xin nghỉ (chỉ khi status = PENDING)
    void cancelLeaveRequest(Integer leaveRequestId, Integer userId);

    // HR xem tất cả đơn xin nghỉ pending
    // HR xem tất cả đơn xin nghỉ pending
    List<LeaveRequestResponse> getAllPendingLeaveRequests();

    // Admin xem tất cả đơn xin nghỉ pending admin
    List<LeaveRequestResponse> getAllPendingAdminLeaveRequests();

    java.util.Map<String, Long> getLeaveRequestCounts();

    // HR xem tất cả đơn xin nghỉ với phân trang
    Page<LeaveRequestResponse> getAllLeaveRequests(String status, int page, int size);

    // HR duyệt/từ chối đơn xin nghỉ
    LeaveRequestResponse processLeaveRequest(Integer hrUserId, LeaveRequestRequest request);

    // Lấy danh sách đơn xin nghỉ đã được duyệt trong khoảng thời gian (cho tạo schedule)
    List<LeaveRequestResponse> getApprovedLeaveRequestsInDateRange(java.time.LocalDate startDate, java.time.LocalDate endDate);
}
