package com.fixflow.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    @Schema(name = "CreateUserRequest")
    public record CreateUserRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 32) String phone,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotEmpty Set<String> roles,
            Boolean mustChangePassword
    ) {
    }

    @Schema(name = "UpdateUserRequest")
    public record UpdateUserRequest(
            @NotBlank @Size(max = 160) String fullName,
            @Size(max = 32) String phone,
            Set<String> roles,
            Boolean active
    ) {
    }

    @Schema(name = "UserResponse")
    public record UserResponse(
            UUID id,
            String fullName,
            String email,
            String phone,
            String avatarUrl,
            boolean active,
            boolean mustChangePassword,
            Instant lastLoginAt,
            Set<String> roles,
            Set<String> permissions
    ) {
    }

    @Schema(name = "RoleResponse")
    public record RoleResponse(
            UUID id,
            String code,
            String name,
            String description,
            boolean systemRole,
            int seniority,
            Set<String> permissions
    ) {
    }
}
