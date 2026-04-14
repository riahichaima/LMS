package com.course.lms.dto;

import jakarta.validation.constraints.NotBlank;

public class CourseRequest {

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    private String description;
    
    private String pdfFile; // Pour le nom du fichier PDF uploadé
    private String pptFile; // Pour le nom du fichier PPT uploadé

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPdfFile() { return pdfFile; }
    public void setPdfFile(String pdfFile) { this.pdfFile = pdfFile; }
    public String getPptFile() { return pptFile; }
    public void setPptFile(String pptFile) { this.pptFile = pptFile; }
}
