package com.course.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LmsAiQuestionResponse {
    private String answer;
    private String agent;
    private List<Object> sources;
}
