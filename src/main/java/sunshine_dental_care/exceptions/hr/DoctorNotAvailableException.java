package sunshine_dental_care.exceptions.hr;


  //Exception khi bác sĩ không có sẵn (đã được phân công)
 //Tác dụng: Xử lý riêng cho trường hợp conflict bác sĩ
 
public class DoctorNotAvailableException extends ScheduleException {
    
    private final Integer doctorId;
    private final String workDate;
    
    public DoctorNotAvailableException(Integer doctorId, String workDate) {
        super(String.format("Doctor ID %d is not available on %s", doctorId, workDate));
        this.doctorId = doctorId;
        this.workDate = workDate;
    }
    
    public Integer getDoctorId() {
        return doctorId;
    }
    
    public String getWorkDate() {
        return workDate;
    }
}
