package com.course.lms.dto;

import com.course.lms.model.ResourceType;

public class LessonResourceResponse {

    private Long id;
    private ResourceType resourceType;
    private String title;
    private String filePath;
    private String externalUrl;
    private String mimeType;
    private Long size;

    public LessonResourceResponse() {}

    public LessonResourceResponse(Long id, ResourceType resourceType, String title, String filePath, 
                                  String externalUrl, String mimeType, Long size) {
        this.id = id;
        this.resourceType = resourceType;
        this.title = title;
        this.filePath = filePath;
        this.externalUrl = externalUrl;
        this.mimeType = mimeType;
        this.size = size;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ResourceType getResourceType() { return resourceType; }
    public void setResourceType(ResourceType resourceType) { this.resourceType = resourceType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
}
