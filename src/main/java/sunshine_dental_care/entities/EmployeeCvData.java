package sunshine_dental_care.entities;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "EmployeeCvData")
public class EmployeeCvData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;
    
    @Column(name = "userId", nullable = false)
    private Integer userId;  // // Liên kết đến User
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", insertable = false, updatable = false)
    private User user;
    
    @Nationalized
    @Column(name = "originalFileName", length = 200)
    private String originalFileName;  // // Tên file CV gốc từ khách hàng
    
    @Nationalized
    @Column(name = "fileType", length = 50)
    private String fileType;  // // Định dạng file (PDF, DOC, DOCX)
    
    @Column(name = "fileSize")
    private Long fileSize;  // // Kích thước file
    
    @Nationalized
    @Column(name = "cvFileUrl", length = 400)
    private String cvFileUrl;
    
    @Column(name = "cvFilePublicId", length = 400)
    private String cvFilePublicId;
    
    @Nationalized
    @Column(name = "extractedText", columnDefinition = "NVARCHAR(MAX)")
    private String extractedText; // // Nội dung text sau khi trích xuất
    
    @Nationalized
    @Column(name = "extractedImages", columnDefinition = "NVARCHAR(MAX)")
    private String extractedImages; // // JSON chứa danh sách link ảnh trích xuất từ CV
    
    @ColumnDefault("sysutcdatetime()")
    @Column(name = "createdAt", nullable = false)
    private Instant createdAt;
    
    @ColumnDefault("sysutcdatetime()")
    @Column(name = "updatedAt", nullable = false)
    private Instant updatedAt;
    
    public EmployeeCvData() {}
    
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
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    public String getOriginalFileName() {
        return originalFileName;
    }
    
    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }
    
    public String getFileType() {
        return fileType;
    }
    
    public void setFileType(String fileType) {
        this.fileType = fileType;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public String getCvFileUrl() {
        return cvFileUrl;
    }
    
    public void setCvFileUrl(String cvFileUrl) {
        this.cvFileUrl = cvFileUrl;
    }
    
    public String getCvFilePublicId() {
        return cvFilePublicId;
    }
    
    public void setCvFilePublicId(String cvFilePublicId) {
        this.cvFilePublicId = cvFilePublicId;
    }
    
    public String getExtractedText() {
        return extractedText;
    }
    
    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }
    
    public String getExtractedImages() {
        return extractedImages;
    }
    
    public void setExtractedImages(String extractedImages) {
        this.extractedImages = extractedImages;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
