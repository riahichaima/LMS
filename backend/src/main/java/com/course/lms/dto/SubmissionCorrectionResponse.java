package com.course.lms.dto;

public class SubmissionCorrectionResponse {
    private Boolean success;
    private Double score;
    private Double maxScore;
    private String feedback;
    private String detailedExplanation;
    private String recommendedLevel;

    public SubmissionCorrectionResponse() {}

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public Double getMaxScore() { return maxScore; }
    public void setMaxScore(Double maxScore) { this.maxScore = maxScore; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public String getDetailedExplanation() { return detailedExplanation; }
    public void setDetailedExplanation(String detailedExplanation) { this.detailedExplanation = detailedExplanation; }

    public String getRecommendedLevel() { return recommendedLevel; }
    public void setRecommendedLevel(String recommendedLevel) { this.recommendedLevel = recommendedLevel; }
}
