package sunshine_dental_care.dto.hrDTO;

public class RoomResponse {
    private Integer id;
    private String roomName;
    private Integer clinicId;
    private String clinicName;
    
    public RoomResponse() {}
    
    public RoomResponse(Integer id, String roomName) {
        this.id = id;
        this.roomName = roomName;
    }
    
    public RoomResponse(Integer id, String roomName, Integer clinicId, String clinicName) {
        this.id = id;
        this.roomName = roomName;
        this.clinicId = clinicId;
        this.clinicName = clinicName;
    }
    
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getRoomName() {
        return roomName;
    }
    
    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }
    
    public Integer getClinicId() {
        return clinicId;
    }
    
    public void setClinicId(Integer clinicId) {
        this.clinicId = clinicId;
    }
    
    public String getClinicName() {
        return clinicName;
    }
    
    public void setClinicName(String clinicName) {
        this.clinicName = clinicName;
    }
}

