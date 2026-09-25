package com.fixflow.user.web;

import com.fixflow.common.web.PageResponse;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import com.fixflow.user.dto.UserDtos.CreateUserRequest;
import com.fixflow.user.dto.UserDtos.RoleResponse;
import com.fixflow.user.dto.UserDtos.UpdateUserRequest;
import com.fixflow.user.dto.UserDtos.UserResponse;
import com.fixflow.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mobistack")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    @GetMapping("/users")
    @PreAuthorize(Authorize.USER_READ)
    public PageResponse<UserResponse> list(@PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(userService.list(CurrentUser.shopId(), pageable));
    }

    @GetMapping(value = "/users", params = "id")
    @PreAuthorize(Authorize.USER_READ)
    public UserResponse get(@RequestParam UUID id) {
        return userService.get(CurrentUser.shopId(), id);
    }

    @PostMapping("/users")
    @PreAuthorize(Authorize.USER_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(CurrentUser.shopId(), request);
    }

    @PutMapping("/users")
    @PreAuthorize(Authorize.USER_WRITE)
    public UserResponse update(@RequestParam UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(CurrentUser.shopId(), id, request);
    }

    @GetMapping("/roles")
    @PreAuthorize(Authorize.USER_READ)
    @Operation(summary = "Roles this shop can assign")
    public List<RoleResponse> roles() {
        return userService.listRoles(CurrentUser.shopId());
    }
}
