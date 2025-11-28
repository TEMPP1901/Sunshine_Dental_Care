package sunshine_dental_care.dto.hrDTO.mapper;

import org.springframework.stereotype.Component;

import sunshine_dental_care.dto.hrDTO.AttendanceResponse;
import sunshine_dental_care.dto.hrDTO.WiFiValidationResult;
import sunshine_dental_care.entities.Attendance;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.EmployeeFaceProfile;
import sunshine_dental_care.services.interfaces.hr.FaceRecognitionService.FaceVerificationResult;

/**
 * Mapper để chuyển đổi Attendance entity sang AttendanceResponse DTO.
 * Tách logic mapping từ AttendanceServiceImpl để code gọn hơn, theo pattern của Reception module.
 */
@Component
public class AttendanceMapper {

    /**
     * Map Attendance entity sang AttendanceResponse DTO.
     * 
     * @param attendance Attendance entity
     * @param user User entity (có thể null)
     * @param clinic Clinic entity (có thể null)
     * @param faceProfile EmployeeFaceProfile (có thể null)
     * @param faceResult FaceVerificationResult (có thể null)
     * @param wifiResult WiFiValidationResult (có thể null)
     * @return AttendanceResponse DTO
     */
    public AttendanceResponse mapToAttendanceResponse(
            Attendance attendance,
            sunshine_dental_care.entities.User user,
            Clinic clinic,
            EmployeeFaceProfile faceProfile,
            FaceVerificationResult faceResult,
            WiFiValidationResult wifiResult) {
        
        if (attendance == null) return null;

        AttendanceResponse response = new AttendanceResponse();
        
        // Basic fields
        response.setId(attendance.getId());
        response.setUserId(attendance.getUserId());
        response.setClinicId(attendance.getClinicId());
        response.setWorkDate(attendance.getWorkDate());
        response.setCheckInTime(attendance.getCheckInTime());
        response.setCheckOutTime(attendance.getCheckOutTime());
        response.setCheckInMethod(attendance.getCheckInMethod());
        response.setIsOvertime(attendance.getIsOvertime());
        response.setNote(attendance.getNote());
        response.setFaceMatchScore(attendance.getFaceMatchScore());
        response.setVerificationStatus(attendance.getVerificationStatus());
        response.setAttendanceStatus(attendance.getAttendanceStatus());
        response.setShiftType(attendance.getShiftType());
        response.setActualWorkHours(attendance.getActualWorkHours());
        response.setExpectedWorkHours(attendance.getExpectedWorkHours());
        response.setLateMinutes(attendance.getLateMinutes());
        response.setEarlyMinutes(attendance.getEarlyMinutes());
        response.setLunchBreakMinutes(attendance.getLunchBreakMinutes());
        response.setCreatedAt(attendance.getCreatedAt());
        response.setUpdatedAt(attendance.getUpdatedAt());

        // Map User fields
        if (user != null) {
            response.setUserName(user.getFullName());
            response.setUserAvatarUrl(user.getAvatarUrl());
            response.setFaceImageUrl(user.getAvatarUrl());
        }

        // Map Clinic fields
        if (clinic != null) {
            response.setClinicName(clinic.getClinicName());
        }

        // Map WiFi validation result
        if (wifiResult != null) {
            response.setWifiValid(wifiResult.isValid());
            response.setWifiValidationMessage(wifiResult.getMessage());
        }

        return response;
    }
}

