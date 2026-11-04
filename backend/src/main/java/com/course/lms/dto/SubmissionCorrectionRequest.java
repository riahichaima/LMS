package com.course.lms.dto;

public class SubmissionCorrectionRequest {
    private Long submissionId;
    private Long assignmentId;
    private String studentAnswer;
    private String assignmentContent;
    private Long studentId;

    public SubmissionCorrectionRequest() {}

    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }

    public Long getAssignmentId() { return assignmentId; }
    public void setAssignmentId(Long assignmentId) { this.assignmentId = assignmentId; }

    public String getStudentAnswer() { return studentAnswer; }
    public void setStudentAnswer(String studentAnswer) { this.studentAnswer = studentAnswer; }

    public String getAssignmentContent() { return assignmentContent; }
    public void setAssignmentContent(String assignmentContent) { this.assignmentContent = assignmentContent; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
}
