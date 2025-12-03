package sunshine_dental_care.entities;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "EmployeeFaceProfiles")
public class EmployeeFaceProfile {
    
    @Id
    @Column(name = "userId", nullable = false)
    private Integer userId;  // FK to User, also PK
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", insertable = false, updatable = false)
    private User user;
    
    @Column(name = "faceEmbedding", columnDefinition = "NVARCHAR(MAX)")
    private String faceEmbedding;  // JSON array of 512 floats: "[0.123, 0.456, ...]"
    
    @Nationalized
    @Column(name = "faceImageUrl", length = 400)
    private String faceImageUrl;  // URL of face crop image for display
    
    // Constructors
    public EmployeeFaceProfile() {}
    
    public EmployeeFaceProfile(Integer userId, String faceEmbedding, String faceImageUrl) {
        this.userId = userId;
        this.faceEmbedding = faceEmbedding;
        this.faceImageUrl = faceImageUrl;
    }
    
    // Getters and Setters
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
    
    public String getFaceEmbedding() {
        return faceEmbedding;
    }
    
    public void setFaceEmbedding(String faceEmbedding) {
        this.faceEmbedding = faceEmbedding;
    }
    
    public String getFaceImageUrl() {
        return faceImageUrl;
    }
    
    public void setFaceImageUrl(String faceImageUrl) {
        this.faceImageUrl = faceImageUrl;
    }
}

