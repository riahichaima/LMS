package com.course.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LmsAiVideoUploadResponse {
    private Integer id;
    private String title;
    private String contentType;
    private String transcriptPreview;
    private Integer segmentsCount;
    private String message;
}
