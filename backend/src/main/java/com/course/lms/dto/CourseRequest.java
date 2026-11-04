package com.course.lms.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class CourseRequest {

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    private String description;
    private Long teacherId; // Optionnel, utilisé par l'admin
    private String status;  // DRAFT, PUBLISHED, etc.
    private String thumbnail;
    private String pricingType; // FREE ou PAID
    private java.math.BigDecimal price; // USD, pour un cours PAID
    
    private List<ChapterRequest> chapters;

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }

    public List<ChapterRequest> getChapters() { return chapters; }
    public void setChapters(List<ChapterRequest> chapters) { this.chapters = chapters; }

    public String getPricingType() { return pricingType; }
    public void setPricingType(String pricingType) { this.pricingType = pricingType; }
    public java.math.BigDecimal getPrice() { return price; }
    public void setPrice(java.math.BigDecimal price) { this.price = price; }
}
