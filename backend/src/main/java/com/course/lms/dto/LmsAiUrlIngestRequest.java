package com.course.lms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LmsAiUrlIngestRequest {
    @NotBlank(message = "L'URL est obligatoire")
    private String url;
    
    private String title;
}
