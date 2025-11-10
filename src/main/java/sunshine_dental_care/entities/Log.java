package sunshine_dental_care.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Entity
@Table(name = "Logs")
public class Log {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "logId", nullable = false)
    private Integer id;

    @Column(name = "userId")
    private Integer userId;

    @Column(name = "clinicId")
    private Integer clinicId;

    @Nationalized
    @Column(name = "\"action\"", nullable = false, length = 100)
    private String action;

    @Nationalized
    @Column(name = "tableName", length = 128)
    private String tableName;

    @Column(name = "recordId")
    private Integer recordId;

    @Nationalized
    @Lob
    @Column(name = "beforeData")
    private String beforeData;

    @Nationalized
    @Lob
    @Column(name = "afterData")
    private String afterData;

    @Nationalized
    @Column(name = "ipAddr", length = 50)
    private String ipAddr;

    @Nationalized
    @Column(name = "userAgent", length = 300)
    private String userAgent;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "actionTime", nullable = false)
    private Instant actionTime;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getClinicId() {
        return clinicId;
    }

    public void setClinicId(Integer clinicId) {
        this.clinicId = clinicId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Integer getRecordId() {
        return recordId;
    }

    public void setRecordId(Integer recordId) {
        this.recordId = recordId;
    }

    public String getBeforeData() {
        return beforeData;
    }

    public void setBeforeData(String beforeData) {
        this.beforeData = beforeData;
    }

    public String getAfterData() {
        return afterData;
    }

    public void setAfterData(String afterData) {
        this.afterData = afterData;
    }

    public String getIpAddr() {
        return ipAddr;
    }

    public void setIpAddr(String ipAddr) {
        this.ipAddr = ipAddr;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Instant getActionTime() {
        return actionTime;
    }

    public void setActionTime(Instant actionTime) {
        this.actionTime = actionTime;
    }

}