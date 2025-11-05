package sunshine_dental_care.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Entity
@Table(name = "MedicalRecordImages")
public class MedicalRecordImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "imageId", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "imageUrl", nullable = false, length = 500)
    private String imageUrl;

    @Nationalized
    @Column(name = "description", length = 400)
    private String description;

    @Nationalized
    @Column(name = "aiTag", length = 120)
    private String aiTag;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "createdAt", nullable = false)
    private Instant createdAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }


    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAiTag() {
        return aiTag;
    }

    public void setAiTag(String aiTag) {
        this.aiTag = aiTag;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

}