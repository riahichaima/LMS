package com.course.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour envoyer un message via REST ou WebSocket.
 */
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {
    private Long receiverId;
    private String content;

    // Getters and Setters
    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
