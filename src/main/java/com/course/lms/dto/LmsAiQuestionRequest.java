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
public class LmsAiQuestionRequest {
    private Integer contentId;
    
    @NotBlank(message = "La question est obligatoire")
    private String question;
    
    private String level; // e.g., "beginner", "intermediate", "advanced"
    private String rawContent;
}
