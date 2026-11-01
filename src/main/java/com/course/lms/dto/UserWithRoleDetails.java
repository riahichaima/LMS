package com.course.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserWithRoleDetails {
    private Long id;
    private String name;
    private String email;
    private RoleDetails role;
    private boolean enabled;
    private String approvalStatus;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleDetails {
        private Long roleId;
        private String roleName;
    }
}
