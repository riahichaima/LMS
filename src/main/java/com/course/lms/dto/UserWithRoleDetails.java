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
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleDetails {
        private Long roleId;
        private String roleName;
    }
}
