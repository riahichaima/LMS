package com.course.lms.dto;

import com.course.lms.model.ResourceType;

public class LessonResourceRequest {

    private ResourceType resourceType;
    private String title;
    private String externalUrl;
    private String fileName;
    
    // Non-exposed directly via API inputs but populated by the controller during file processing
    private String filePath;
    private String mimeType;
    private Long size;

    public LessonResourceRequest() {}

    public LessonResourceRequest(ResourceType resourceType, String title, String externalUrl, String fileName) {
        this.resourceType = resourceType;
        this.title = title;
        this.externalUrl = externalUrl;
        this.fileName = fileName;
    }

    public ResourceType getResourceType() { return resourceType; }
    public void setResourceType(ResourceType resourceType) { this.resourceType = resourceType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
}
