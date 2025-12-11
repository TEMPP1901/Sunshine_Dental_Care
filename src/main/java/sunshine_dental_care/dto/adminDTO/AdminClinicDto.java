package sunshine_dental_care.dto.adminDTO;

import java.time.Instant;

public class AdminClinicDto {

    private Integer id;
    private String clinicCode;
    private String clinicName;
    private String address;
    private String phone;
    private String email;
    private String openingHours;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    // Lấy Id của phòng khám
    public Integer getId() {
        return id;
    }

    // Đặt Id cho phòng khám
    public void setId(Integer id) {
        this.id = id;
    }

    // Lấy mã phòng khám
    public String getClinicCode() {
        return clinicCode;
    }

    // Đặt mã phòng khám
    public void setClinicCode(String clinicCode) {
        this.clinicCode = clinicCode;
    }

    // Lấy tên phòng khám
    public String getClinicName() {
        return clinicName;
    }

    // Đặt tên phòng khám
    public void setClinicName(String clinicName) {
        this.clinicName = clinicName;
    }

    // Lấy địa chỉ phòng khám
    public String getAddress() {
        return address;
    }

    // Đặt địa chỉ phòng khám
    public void setAddress(String address) {
        this.address = address;
    }

    // Lấy số điện thoại phòng khám
    public String getPhone() {
        return phone;
    }

    // Đặt số điện thoại phòng khám
    public void setPhone(String phone) {
        this.phone = phone;
    }

    // Lấy email phòng khám
    public String getEmail() {
        return email;
    }

    // Đặt email phòng khám
    public void setEmail(String email) {
        this.email = email;
    }

    // Lấy giờ mở cửa phòng khám
    public String getOpeningHours() {
        return openingHours;
    }

    // Đặt giờ mở cửa phòng khám
    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }

    // Kiểm tra trạng thái hoạt động của phòng khám
    public Boolean getActive() {
        return active;
    }

    // Cập nhật trạng thái hoạt động của phòng khám
    public void setActive(Boolean active) {
        this.active = active;
    }

    // Lấy thời điểm tạo phòng khám
    public Instant getCreatedAt() {
        return createdAt;
    }

    // Đặt thời điểm tạo phòng khám
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    // Lấy thời điểm cập nhật phòng khám lần cuối
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Đặt thời điểm cập nhật phòng khám lần cuối
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
