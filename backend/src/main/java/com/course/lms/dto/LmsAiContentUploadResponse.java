package com.course.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LmsAiContentUploadResponse {
    private Integer id;
    private String title;
    private String contentType;
    private String message;
    private String textPreview;
}
